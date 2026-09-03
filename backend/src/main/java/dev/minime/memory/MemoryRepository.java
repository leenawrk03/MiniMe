package dev.minime.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Raw JDBC because pgvector's {@code vector} type has no JPA mapping.
 * Vectors are passed as their literal text form and cast in SQL.
 */
@Repository
public class MemoryRepository {

    private final JdbcTemplate jdbc;

    public MemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS =
            "id, content, kind, source, confidence, pinned, created_at, last_used_at";

    private static final RowMapper<MemoryFact> MAPPER = (rs, i) -> new MemoryFact(
            rs.getObject("id", UUID.class),
            rs.getString("content"),
            rs.getString("kind"),
            rs.getString("source"),
            rs.getDouble("confidence"),
            rs.getBoolean("pinned"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("last_used_at") == null ? null : rs.getTimestamp("last_used_at").toInstant(),
            null);

    public UUID insert(String content, String kind, String source, double confidence, float[] embedding) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO memory_fact (id, content, kind, source, confidence, embedding)
                VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
                """, id, content, kind, source, confidence, toVectorLiteral(embedding));
        return id;
    }

    public List<MemoryFact> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM memory_fact ORDER BY pinned DESC, created_at DESC", MAPPER);
    }

    /** Cosine-nearest facts above the given similarity floor. */
    public List<MemoryFact> search(float[] embedding, int limit, double minSimilarity) {
        String vec = toVectorLiteral(embedding);
        return jdbc.query("""
                SELECT %s, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                FROM memory_fact
                WHERE embedding IS NOT NULL
                  AND 1 - (embedding <=> CAST(? AS vector)) >= ?
                ORDER BY pinned DESC, embedding <=> CAST(? AS vector)
                LIMIT ?
                """.formatted(COLS),
                (rs, i) -> MAPPER.mapRow(rs, i).withSimilarity(rs.getDouble("similarity")),
                vec, vec, minSimilarity, vec, limit);
    }

    /** Used to avoid storing near-duplicate facts. */
    public double bestSimilarity(float[] embedding) {
        String vec = toVectorLiteral(embedding);
        Double best = jdbc.query("""
                SELECT 1 - (embedding <=> CAST(? AS vector)) AS similarity
                FROM memory_fact WHERE embedding IS NOT NULL
                ORDER BY embedding <=> CAST(? AS vector) LIMIT 1
                """, rs -> rs.next() ? rs.getDouble("similarity") : null, vec, vec);
        return best == null ? 0d : best;
    }

    public void touch(List<UUID> ids) {
        for (UUID id : ids) {
            jdbc.update("UPDATE memory_fact SET last_used_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now()), id);
        }
    }

    public void updateContent(UUID id, String content, float[] embedding) {
        jdbc.update("UPDATE memory_fact SET content = ?, embedding = CAST(? AS vector) WHERE id = ?",
                content, toVectorLiteral(embedding), id);
    }

    public void setPinned(UUID id, boolean pinned) {
        jdbc.update("UPDATE memory_fact SET pinned = ? WHERE id = ?", pinned, id);
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM memory_fact WHERE id = ?", id);
    }

    public void deleteAll() {
        jdbc.update("DELETE FROM memory_fact");
    }

    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
