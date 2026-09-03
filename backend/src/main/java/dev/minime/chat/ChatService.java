package dev.minime.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.minime.config.LlmConfig.GeminiModel;
import dev.minime.config.LlmConfig.GeminiModelChain;
import dev.minime.config.MiniMeProperties;
import dev.minime.memory.MemoryService;
import dev.minime.skills.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** One long conversation; the prompt is windowed to stay cheap. */
    private static final int PROMPT_WINDOW = 12;

    /** Fact extraction runs off the request thread so replies never wait on it. */
    private static final java.util.concurrent.ExecutorService BACKGROUND =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "minime-memory");
                t.setDaemon(true);
                return t;
            });

    private final ChatMessageRepository repository;
    private final MiniMeProperties props;
    private final MemoryService memory;
    private final SkillService skills;
    private final ChatLanguageModel model;
    private final List<GeminiModel> modelChain;

    public ChatService(
            ChatMessageRepository repository,
            MiniMeProperties props,
            MemoryService memory,
            SkillService skills,
            @Autowired(required = false)
            @Qualifier("primaryChatLanguageModel")
            @Nullable ChatLanguageModel model,
            @Autowired(required = false)
            @Nullable GeminiModelChain modelChain) {

        this.repository = repository;
        this.props = props;
        this.memory = memory;
        this.skills = skills;
        this.model = model;
        this.modelChain = modelChain == null ? List.of() : modelChain.models();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> history() {
        return repository.findByConversationIdOrderByCreatedAtAsc(
                props.getConversationId()
        );
    }

    /**
     * Phase 2 turn: recall memory + active skills, answer, persist, then extract
     * new long-term facts from what the user just said.
     */
    public ChatMessageEntity reply(String userText) {

        String conv = props.getConversationId();

        List<ChatMessageEntity> priorHistory = history();

        repository.save(
                ChatMessageEntity.of(conv, "user", userText)
        );

        String recalled = safe(
                () -> memory.recallBlock(userText)
        );

        String skillBlock = safe(
                () -> skills.promptBlock(userText)
        );

        String reminderBlock = safe(
                skills::reminderBlock
        );

        String answer;

        if (model == null) {

            answer = "I'm running without a Gemini API key, so I can only echo: "
                    + userText
                    + (recalled.isBlank()
                    ? ""
                    : "\n\n(But I do remember: "
                    + recalled.trim()
                    + ")");

        } else {

            List<ChatMessage> prompt = buildPrompt(
                    priorHistory,
                    userText,
                    recalled,
                    skillBlock,
                    reminderBlock
            );

            try {

                answer = generateWithFallback(prompt);

            } catch (Exception e) {

                log.error("All LLM models failed", e);

                answer = "My language model is busy right now - I tried "
                        + "every model I have. Give me a few seconds and ask "
                        + "again.";
            }
        }

        ChatMessageEntity saved = repository.save(
                ChatMessageEntity.of(conv, "assistant", answer)
        );

        BACKGROUND.submit(() -> {
            try {
                memory.remember(userText);
            } catch (Exception e) {
                log.warn("Memory extraction failed: {}", e.getMessage());
            }
        });

        return saved;
    }

    /**
     * Walk the configured Gemini model chain.
     *
     * Each model is retried with exponential backoff while Google reports a
     * temporary overload (503 UNAVAILABLE / RESOURCE_EXHAUSTED / 429). When a
     * model is permanently unusable - 404 NOT_FOUND because the name was
     * retired - we skip straight to the next model in the chain. Any other
     * error is a real bug and propagates immediately.
     */
    private String generateWithFallback(List<ChatMessage> prompt) {

        List<GeminiModel> chain = modelChain.isEmpty()
                ? List.of(new GeminiModel(props.getGemini().getModel(), model))
                : modelChain;

        RuntimeException lastError = null;

        for (GeminiModel candidate : chain) {

            int attempts = Math.max(1, props.getGemini().getMaxRetries());
            long backoff = Math.max(100, props.getGemini().getRetryBackoffMs());

            for (int attempt = 1; attempt <= attempts; attempt++) {

                try {

                    log.debug("Calling Gemini model {} (attempt {}/{})",
                            candidate.name(), attempt, attempts);

                    Response<AiMessage> response =
                            candidate.model().generate(prompt);

                    return response.content().text();

                } catch (RuntimeException error) {

                    lastError = error;

                    if (isQuotaExhausted(error)) {

                        log.warn("Model {} is out of quota ({}). Switching model immediately.",
                                candidate.name(), rootMessage(error));

                        break;
                    }

                    if (isModelUnavailable(error)) {

                        log.warn("Model {} is not available ({}). Trying the next model.",
                                candidate.name(), rootMessage(error));

                        break;
                    }

                    if (!isTemporarilyOverloaded(error)) {
                        throw error;
                    }

                    if (attempt == attempts) {

                        log.warn("Model {} still overloaded after {} attempts. Trying the next model.",
                                candidate.name(), attempts);

                        break;
                    }

                    log.warn("Model {} overloaded (attempt {}/{}), retrying in {} ms",
                            candidate.name(), attempt, attempts, backoff);

                    sleep(backoff);
                    backoff *= 2;
                }
            }
        }

        throw lastError != null
                ? lastError
                : new IllegalStateException("No Gemini model is configured.");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Free-tier quota is exhausted for this model - the reset is a minute away,
     * so retrying here only adds latency. Move to the next model at once.
     */
    private boolean isQuotaExhausted(Throwable error) {
        String lower = allMessages(error);
        return lower.contains("quota")
                || lower.contains("exceeded your current quota")
                || lower.contains("resource_exhausted");
    }

    /** Temporary capacity failures - worth retrying the same model. */
    private boolean isTemporarilyOverloaded(Throwable error) {
        String lower = allMessages(error);
        return lower.contains("503")
                || lower.contains("unavailable")
                || lower.contains("high demand")
                || lower.contains("overloaded")
                || lower.contains("429")
                || lower.contains("rate limit")
                || lower.contains("deadline")
                || lower.contains("timeout")
                || lower.contains("500")
                || lower.contains("internal error");
    }

    /** Permanent failures for this model name - skip to the next model. */
    private boolean isModelUnavailable(Throwable error) {
        String lower = allMessages(error);
        return lower.contains("404")
                || lower.contains("not_found")
                || lower.contains("not found")
                || lower.contains("no longer available")
                || lower.contains("is not supported")
                || lower.contains("unsupported model");
    }

    private String allMessages(Throwable error) {
        StringBuilder all = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (current.getMessage() != null) {
                all.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return all.toString().toLowerCase();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    @Transactional
    public void clear() {

        repository.deleteByConversationId(
                props.getConversationId()
        );
    }

    private List<ChatMessage> buildPrompt(
            List<ChatMessageEntity> stored,
            String latestUserText,
            String recalled,
            String skillBlock,
            String reminderBlock) {

        StringBuilder system =
                new StringBuilder(props.getSystemPrompt());

        if (!recalled.isBlank()) {

            system.append("\n\n")
                    .append(recalled);
        }

        if (!skillBlock.isBlank()) {

            system.append('\n')
                    .append(skillBlock);
        }

        if (!reminderBlock.isBlank()) {

            system.append('\n')
                    .append(reminderBlock);
        }

        system.append(
                "\nUse remembered facts naturally; "
                        + "never claim to remember something not listed."
        );

        List<ChatMessage> messages = new ArrayList<>();

        messages.add(
                SystemMessage.from(system.toString())
        );

        int from = Math.max(
                0,
                stored.size() - PROMPT_WINDOW
        );

        for (ChatMessageEntity e :
                stored.subList(from, stored.size())) {

            messages.add(
                    "assistant".equals(e.getRole())
                            ? AiMessage.from(e.getContent())
                            : UserMessage.from(e.getContent())
            );
        }

        messages.add(
                UserMessage.from(latestUserText)
        );

        return messages;
    }

    private String safe(
            java.util.function.Supplier<String> supplier) {

        try {

            return supplier.get();

        } catch (Exception e) {

            log.warn(
                    "Context lookup failed: {}",
                    e.getMessage()
            );

            return "";
        }
    }
}