package dev.minime.memory;

import java.time.Instant;
import java.util.UUID;

/**
 * A single thing MiniMe knows about you. The embedding never leaves the
 * repository layer, so this record stays JSON-friendly for the UI.
 */
public record MemoryFact(
        UUID id,
        String content,
        String kind,
        String source,
        double confidence,
        boolean pinned,
        Instant createdAt,
        Instant lastUsedAt,
        Double similarity) {

    public MemoryFact withSimilarity(Double value) {
        return new MemoryFact(id, content, kind, source, confidence, pinned, createdAt, lastUsedAt, value);
    }
}
