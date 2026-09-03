package dev.minime.memory;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.minime.config.MiniMeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent memory: extracts durable facts from what you say, stores them as
 * pgvector embeddings, and recalls the relevant ones for each new turn.
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private static final String EXTRACTION_PROMPT = """
            You extract long-term facts about the user from a single message.
            Return ONLY durable, personally useful facts (name, preferences, people,
            projects, goals, routines, constraints). Ignore small talk, questions and
            anything transient.
            Output one fact per line, third person, starting with "User ". No bullets,
            no numbering, no commentary. If there is nothing worth remembering, output
            exactly: NONE
            """;

    private final MemoryRepository repository;
    private final EmbeddingProvider embeddings;
    private final MiniMeProperties props;
    private final ChatLanguageModel model;

    public MemoryService(MemoryRepository repository,
                         EmbeddingProvider embeddings,
                         MiniMeProperties props,
                         @Autowired(required = false) @Nullable ChatLanguageModel model) {
        this.repository = repository;
        this.embeddings = embeddings;
        this.props = props;
        this.model = model;
    }

    public List<MemoryFact> all() {
        return repository.findAll();
    }

    public MemoryFact add(String content, String kind, String source, double confidence) {
        String clean = content.trim();
        float[] vector = embeddings.embed(clean);
        UUID id = repository.insert(clean, kind, source, confidence, vector);
        return repository.findAll().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    public void update(UUID id, String content) {
        repository.updateContent(id, content.trim(), embeddings.embed(content.trim()));
    }

    public void setPinned(UUID id, boolean pinned) {
        repository.setPinned(id, pinned);
    }

    public void delete(UUID id) {
        repository.delete(id);
    }

    public void forgetEverything() {
        repository.deleteAll();
    }

    /** Facts worth injecting into the prompt for this user turn. */
    public List<MemoryFact> recall(String query) {
        MiniMeProperties.Memory cfg = props.getMemory();
        List<MemoryFact> hits = repository.search(embeddings.embed(query), cfg.getRecallLimit(), cfg.getMinSimilarity());
        repository.touch(hits.stream().map(MemoryFact::id).toList());
        return hits;
    }

    public String recallBlock(String query) {
        List<MemoryFact> hits = recall(query);
        if (hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("What you remember about the user:\n");
        for (MemoryFact fact : hits) sb.append("- ").append(fact.content()).append('\n');
        return sb.toString();
    }

    /**
     * Extracts and stores facts from a user message. Runs after the reply so it
     * never slows the conversation down.
     */
    public List<MemoryFact> remember(String userText) {
        List<String> candidates = extract(userText);
        List<MemoryFact> saved = new ArrayList<>();
        double dedupe = props.getMemory().getDuplicateSimilarity();
        for (String candidate : candidates) {
            float[] vector = embeddings.embed(candidate);
            if (repository.bestSimilarity(vector) >= dedupe) {
                log.debug("Skipping near-duplicate fact: {}", candidate);
                continue;
            }
            UUID id = repository.insert(candidate, "fact", "chat", 0.8, vector);
            log.debug("Remembered [{}]: {}", id, candidate);
            saved.add(new MemoryFact(id, candidate, "fact", "chat", 0.8, false, java.time.Instant.now(), null, null));
        }
        return saved;
    }

    private List<String> extract(String userText) {
        if (model == null || !props.getMemory().isLlmExtraction()) {
            // Default path: no extra LLM round-trip per turn (faster, no quota burn).
            return heuristicExtract(userText);
        }
        try {
            String raw = model.generate(List.of(
                    SystemMessage.from(EXTRACTION_PROMPT),
                    UserMessage.from(userText))).content().text();
            if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("NONE")) return List.of();
            return raw.lines()
                    .map(l -> l.replaceAll("^[-*\\d.\\s]+", "").trim())
                    .filter(l -> l.length() > 3 && !l.equalsIgnoreCase("NONE"))
                    .limit(5)
                    .toList();
        } catch (Exception e) {
            log.warn("Fact extraction failed ({}), falling back to heuristics", e.getMessage());
            return heuristicExtract(userText);
        }
    }

    /** Offline-mode extraction: catches the most common self-disclosure patterns. */
    private List<String> heuristicExtract(String text) {
        String t = text.trim();
        String lower = t.toLowerCase();
        List<String> facts = new ArrayList<>();
        String[] markers = {"my name is", "i am ", "i'm ", "i like ", "i love ", "i hate ",
                "i work ", "i live ", "i prefer ", "remember that ", "my "};
        for (String marker : markers) {
            int idx = lower.indexOf(marker);
            if (idx >= 0) {
                String fragment = t.substring(idx).split("(?<=[.!?])\\s")[0].trim();
                if (fragment.length() > 4) {
                    facts.add("User said: " + fragment);
                    break;
                }
            }
        }
        return facts;
    }
}
