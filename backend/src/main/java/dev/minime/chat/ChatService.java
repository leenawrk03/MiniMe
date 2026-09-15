package dev.minime.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import java.util.LinkedHashMap;
import dev.minime.config.LlmConfig.GeminiModel;
import dev.minime.config.LlmConfig.GeminiModelChain;
import dev.minime.config.MiniMeProperties;
import dev.minime.memory.MemoryService;
import dev.minime.mcp.McpClientService;
import dev.minime.mcp.GitHubMcpClientService;
import dev.minime.skills.SkillService;

import io.modelcontextprotocol.spec.McpSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log =
            LoggerFactory.getLogger(ChatService.class);

    private static final int PROMPT_WINDOW = 12;

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
    private final McpClientService mcpClient;
    private final GitHubMcpClientService githubMcp;
    private final ObjectMapper objectMapper;

    public ChatService(
            ChatMessageRepository repository,
            MiniMeProperties props,
            MemoryService memory,
            SkillService skills,
            @Autowired(required = false)
            @Qualifier("primaryChatLanguageModel")
            @Nullable ChatLanguageModel model,
            @Autowired(required = false)
            @Nullable GeminiModelChain modelChain,
            McpClientService mcpClient,
            GitHubMcpClientService githubMcp,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.props = props;
        this.memory = memory;
        this.skills = skills;
        this.model = model;
        this.modelChain =
                modelChain == null
                        ? List.of()
                        : modelChain.models();
        this.mcpClient = mcpClient;
        this.githubMcp = githubMcp;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> history() {

        return repository.findByConversationIdOrderByCreatedAtAsc(
                props.getConversationId()
        );
    }

    public ChatMessageEntity reply(String userText) {

        String conv =
                props.getConversationId();

        List<ChatMessageEntity> priorHistory =
                history();

        repository.save(
                ChatMessageEntity.of(
                        conv,
                        "user",
                        userText
                )
        );

        String recalled =
                safe(() ->
                        memory.recallBlock(userText)
                );

        String skillBlock =
                safe(() ->
                        skills.promptBlock(userText)
                );

        String reminderBlock =
                safe(skills::reminderBlock);

        String answer;

        if (model == null) {

            answer =
                    "I'm running without a Gemini API key, so I can only echo: "
                            + userText
                            + (recalled.isBlank()
                            ? ""
                            : "\n\n(But I do remember: "
                            + recalled.trim()
                            + ")");

        } else {

            List<ChatMessage> prompt =
                    buildPrompt(
                            priorHistory,
                            userText,
                            recalled,
                            skillBlock,
                            reminderBlock
                    );

            try {

                answer =
                        generateWithFallback(prompt);

            } catch (Exception e) {

                log.error(
                        "All LLM models failed",
                        e
                );

                answer =
                        "My language model is busy right now - I tried "
                                + "every model I have. Give me a few seconds "
                                + "and ask again.";
            }
        }

        ChatMessageEntity saved =
                repository.save(
                        ChatMessageEntity.of(
                                conv,
                                "assistant",
                                answer
                        )
                );

        BACKGROUND.submit(() -> {

            try {

                memory.remember(userText);

            } catch (Exception e) {

                log.warn(
                        "Memory extraction failed: {}",
                        e.getMessage()
                );
            }
        });

        return saved;
    }

    /**
     * Sends the conversation to Gemini with the MCP tools available.
     *
     * There is deliberately NO confirmation state here.
     *
     * If Gemini decides that a tool matches the user's command,
     * the tool is executed immediately.
     */
    private String generateWithFallback(
            List<ChatMessage> prompt) {

        List<GeminiModel> chain =
                modelChain.isEmpty()
                        ? List.of(
                        new GeminiModel(
                                props.getGemini().getModel(),
                                model
                        )
                )
                        : modelChain;

        RuntimeException lastError = null;

        for (GeminiModel candidate : chain) {

            int attempts =
                    Math.max(
                            1,
                            props.getGemini().getMaxRetries()
                    );

            long backoff =
                    Math.max(
                            100,
                            props.getGemini().getRetryBackoffMs()
                    );

            for (int attempt = 1;
                 attempt <= attempts;
                 attempt++) {

                try {

                    log.debug(
                            "Calling Gemini model {} (attempt {}/{})",
                            candidate.name(),
                            attempt,
                            attempts
                    );

                    return chatWithMcpTools(
                            candidate.model(),
                            prompt
                    );

                } catch (RuntimeException error) {

                    lastError = error;

                    if (isQuotaExhausted(error)) {

                        log.warn(
                                "Model {} is out of quota ({}). "
                                        + "Switching model immediately.",
                                candidate.name(),
                                rootMessage(error)
                        );

                        break;
                    }

                    if (isModelUnavailable(error)) {

                        log.warn(
                                "Model {} is not available ({}). "
                                        + "Trying the next model.",
                                candidate.name(),
                                rootMessage(error)
                        );

                        break;
                    }

                    if (!isTemporarilyOverloaded(error)) {

                        throw error;
                    }

                    if (attempt == attempts) {

                        log.warn(
                                "Model {} still overloaded after {} attempts. "
                                        + "Trying the next model.",
                                candidate.name(),
                                attempts
                        );

                        break;
                    }

                    log.warn(
                            "Model {} overloaded (attempt {}/{}), "
                                    + "retrying in {} ms",
                            candidate.name(),
                            attempt,
                            attempts,
                            backoff
                    );

                    sleep(backoff);

                    backoff *= 2;
                }
            }
        }

        throw lastError != null
                ? lastError
                : new IllegalStateException(
                "No Gemini model is configured."
        );
    }

    /**
     * Gemini <-> MCP tool loop.
     *
     * The model can directly call open_application.
     * No yes/no confirmation is requested or stored.
     */
    private String chatWithMcpTools(
            ChatLanguageModel chatModel,
            List<ChatMessage> messages) {

        ToolSpecification openApplicationTool =
                ToolSpecification.builder()
                        .name("open_application")
                        .description(
                                "Opens an allowed macOS application by exact name. "
                                        + "Use this immediately when the user asks "
                                        + "MiniMe to open an application. "
                                        + "Do not ask for confirmation."
                        )
                        .parameters(
                                ToolParameters.builder()
                                        .type("object")
                                        .properties(
                                                Map.of(
                                                        "appName",
                                                        Map.of(
                                                                "type",
                                                                "string",
                                                                "description",
                                                                "Exact macOS application name, "
                                                                        + "for example Notes, Safari, "
                                                                        + "Google Chrome, Terminal, "
                                                                        + "Finder, Slack, or WhatsApp."
                                                        )
                                                )
                                        )
                                        .required(
                                                List.of("appName")
                                        )
                                        .build()
                        )
                        .build();

        /*
         * GitHub tools are discovered dynamically from the GitHub MCP
         * server (see GitHubMcpClientService) rather than hardcoded, since
         * that server exposes dozens of tools that can change over time.
         * When the server is disabled or unreachable this list is empty
         * and Gemini simply never offers GitHub actions.
         */
        java.util.Map<String, ToolSpecification> githubToolsByName =
                githubToolSpecifications();

        List<ToolSpecification> allTools =
                new ArrayList<>();
        allTools.add(openApplicationTool);
        allTools.addAll(githubToolsByName.values());

        List<ChatMessage> workingMessages =
                new ArrayList<>(messages);

        ChatRequest request =
                ChatRequest.builder()
                        .messages(workingMessages)
                        .toolSpecifications(allTools)
                        .build();

        ChatResponse response =
                chatModel.chat(request);

        AiMessage aiMessage =
                response.aiMessage();

        /*
         * Normal response — no MCP action requested.
         */
        if (!aiMessage.hasToolExecutionRequests()) {

            return aiMessage.text();
        }

        /*
         * Gemini requested one or more MCP tools.
         */
        workingMessages.add(aiMessage);

        for (ToolExecutionRequest toolRequest :
                aiMessage.toolExecutionRequests()) {

            log.info(
                    "Gemini requested MCP tool: {}",
                    toolRequest.name()
            );

            Map<String, Object> arguments;

            try {

                arguments =
                        objectMapper.readValue(
                                toolRequest.arguments(),
                                new TypeReference<Map<String, Object>>() {
                                }
                        );

            } catch (Exception e) {

                log.error(
                        "Could not parse tool arguments: {}",
                        toolRequest.arguments(),
                        e
                );

                workingMessages.add(
                        ToolExecutionResultMessage.from(
                                toolRequest,
                                "Invalid tool arguments: "
                                        + e.getMessage()
                        )
                );

                continue;
            }

            boolean isGithubTool =
                    githubToolsByName.containsKey(
                            toolRequest.name()
                    );

            McpSchema.CallToolResult toolResult =
                    isGithubTool
                            ? githubMcp.callTool(
                                    toolRequest.name(),
                                    arguments
                            )
                            : mcpClient.callTool(
                                    toolRequest.name(),
                                    arguments
                            );

            String resultText =
                    extractToolResultText(toolResult);

            log.info(
                    "MCP tool {} returned: {}",
                    toolRequest.name(),
                    resultText
            );

            workingMessages.add(
                    ToolExecutionResultMessage.from(
                            toolRequest,
                            resultText
                    )
            );
        }

        /*
         * Give Gemini the MCP result so it can produce the
         * final natural-language response.
         */
        ChatRequest finalRequest =
                ChatRequest.builder()
                        .messages(workingMessages)
                        .toolSpecifications(allTools)
                        .build();

        ChatResponse finalResponse =
                chatModel.chat(finalRequest);

        return finalResponse.aiMessage().text();
    }

    /**
     * Converts the GitHub MCP server's tool list into langchain4j
     * ToolSpecifications, keyed by name for quick lookup when routing a
     * tool call. Fails soft: any problem talking to the GitHub MCP
     * container yields an empty map instead of breaking the chat turn.
     */
    private java.util.Map<String, ToolSpecification> githubToolSpecifications() {

        if (!githubMcp.isEnabled()) {
            return java.util.Map.of();
        }

        try {

            McpSchema.ListToolsResult tools =
                    githubMcp.listTools();

            java.util.Map<String, ToolSpecification> specs =
                    new java.util.LinkedHashMap<>();

            for (McpSchema.Tool tool : tools.tools()) {

                McpSchema.JsonSchema schema =
                        tool.inputSchema();

                ToolParameters.Builder paramsBuilder =
                        ToolParameters.builder();

                if (schema != null) {

                    paramsBuilder.type(
                            schema.type() != null
                                    ? schema.type()
                                    : "object"
                    );

                    Map<String, Map<String, Object>> properties =
                            new LinkedHashMap<>();

                    if (schema.properties() != null) {

                        schema.properties().forEach((key, value) -> {

                            if (value instanceof Map<?, ?> map) {

                                Map<String, Object> property =
                                        normalizeSchemaMap(map);

                                properties.put(key, property);
                            }
                        });
                    }

                    paramsBuilder.properties(properties);

                    paramsBuilder.required(
                            schema.required() != null
                                    ? schema.required()
                                    : List.of()
                    );

                } else {

                    paramsBuilder.type("object");
                    paramsBuilder.properties(Map.of());
                    paramsBuilder.required(List.of());
                }

                specs.put(
                        tool.name(),
                        ToolSpecification.builder()
                                .name(tool.name())
                                .description(
                                        tool.description() != null
                                                ? tool.description()
                                                : tool.name()
                                )
                                .parameters(paramsBuilder.build())
                                .build()
                );
            }

            return specs;

        } catch (Exception e) {

            log.warn(
                    "Could not load GitHub MCP tools, skipping for this turn: {}",
                    e.getMessage()
            );

            return java.util.Map.of();
        }
    }

    private Map<String, Object> normalizeSchemaMap(
            Map<?, ?> source) {

        Map<String, Object> normalized =
                new LinkedHashMap<>();

        source.forEach((key, value) -> {

            String propertyKey =
                    String.valueOf(key);

            if ("type".equals(propertyKey)) {

                if (value instanceof List<?> list) {

                    if (!list.isEmpty()) {
                        normalized.put(
                                propertyKey,
                                String.valueOf(list.get(0))
                        );
                    } else {
                        normalized.put(
                                propertyKey,
                                "string"
                        );
                    }

                } else {

                    normalized.put(
                            propertyKey,
                            String.valueOf(value)
                    );
                }

                return;
            }

            if (value instanceof Map<?, ?> nestedMap) {

                normalized.put(
                        propertyKey,
                        normalizeSchemaMap(nestedMap)
                );

                return;
            }

            if (value instanceof List<?> list) {

                List<Object> normalizedList =
                        new ArrayList<>();

                for (Object item : list) {

                    if (item instanceof Map<?, ?> itemMap) {

                        normalizedList.add(
                                normalizeSchemaMap(itemMap)
                        );

                    } else {

                        normalizedList.add(item);
                    }
                }

                normalized.put(
                        propertyKey,
                        normalizedList
                );

                return;
            }

            normalized.put(
                    propertyKey,
                    value
            );
        });

        return normalized;
    }

    private String extractToolResultText(
            McpSchema.CallToolResult result) {

        if (result == null) {
            return "MCP returned no result.";
        }

        if (Boolean.TRUE.equals(result.isError())) {

            return result.content()
                    .stream()
                    .map(content -> {

                        if (content instanceof McpSchema.TextContent text) {
                            return text.text();
                        }

                        return content.toString();
                    })
                    .reduce(
                            "",
                            (a, b) ->
                                    a.isBlank()
                                            ? b
                                            : a + "\n" + b
                    );
        }

        return result.content()
                .stream()
                .map(content -> {

                    if (content instanceof McpSchema.TextContent text) {
                        return text.text();
                    }

                    return content.toString();
                })
                .reduce(
                        "",
                        (a, b) ->
                                a.isBlank()
                                        ? b
                                        : a + "\n" + b
                );
    }

    private void sleep(long millis) {

        try {

            Thread.sleep(millis);

        } catch (InterruptedException interrupted) {

            Thread.currentThread().interrupt();
        }
    }

    private boolean isQuotaExhausted(
            Throwable error) {

        String lower =
                allMessages(error);

        return lower.contains("quota")
                || lower.contains(
                "exceeded your current quota"
        )
                || lower.contains(
                "resource_exhausted"
        );
    }

    private boolean isTemporarilyOverloaded(
            Throwable error) {

        String lower =
                allMessages(error);

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

    private boolean isModelUnavailable(
            Throwable error) {

        String lower =
                allMessages(error);

        return lower.contains("404")
                || lower.contains("not_found")
                || lower.contains("not found")
                || lower.contains("no longer available")
                || lower.contains("is not supported")
                || lower.contains("unsupported model");
    }

    private String allMessages(
            Throwable error) {

        StringBuilder all =
                new StringBuilder();

        Throwable current =
                error;

        int depth = 0;

        while (current != null
                && depth++ < 8) {

            if (current.getMessage() != null) {

                all.append(
                        current.getMessage()
                ).append(' ');
            }

            current =
                    current.getCause();
        }

        return all.toString().toLowerCase();
    }

    private String rootMessage(
            Throwable error) {

        Throwable current =
                error;

        while (current.getCause() != null) {
            current =
                    current.getCause();
        }

        return current.getMessage() == null
                ? current.toString()
                : current.getMessage();
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
                new StringBuilder(
                        props.getSystemPrompt()
                );

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
                        + "never claim to remember something not listed."+
                "\nGitHub instructions: "
                        + "You have access to the user's authenticated GitHub account through the GitHub MCP server. "
                        + "Treat GitHub as account-wide, not limited to a specific repository. "
                        + "The user's GitHub username is leenawrk03. "
                        + "When the user asks about their repositories, first use the appropriate GitHub MCP tool "
                        + "to discover or search their repositories rather than assuming a repository name or owner. "
                        + "You may work with any repository accessible to the authenticated account. "
                        + "You can inspect repositories, files, branches, commits, issues, pull requests, releases, "
                        + "and other available GitHub resources using the appropriate MCP tools. "
                        + "You may create or update files, create branches, create pull requests, manage issues, "
                        + "and perform other available GitHub operations when the user explicitly requests them. "
                        + "For repository-specific operations, identify the correct owner and repository from GitHub "
                        + "tool results before performing the operation. "
                        + "Do not claim that a repository is private, inaccessible, or missing without first using "
                        + "the appropriate GitHub MCP tool to verify it. "
                        + "Do not ask the user for the repository owner when GitHub search results already provide it."

        );

        List<ChatMessage> messages =
                new ArrayList<>();

        messages.add(
                SystemMessage.from(
                        system.toString()
                )
        );

        int from =
                Math.max(
                        0,
                        stored.size() - PROMPT_WINDOW
                );

        for (ChatMessageEntity entity :
                stored.subList(
                        from,
                        stored.size()
                )) {

            messages.add(
                    "assistant".equals(
                            entity.getRole()
                    )
                            ? AiMessage.from(
                            entity.getContent()
                    )
                            : UserMessage.from(
                            entity.getContent()
                    )
            );
        }

        messages.add(
                UserMessage.from(
                        latestUserText
                )
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
