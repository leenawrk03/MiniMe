package dev.minime.memory;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the LangChain4j Gemini embedding model and falls back to the local
 * deterministic embedder if the API call fails, so memory keeps working offline.
 */
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingProvider.class);

    private final EmbeddingModel model;
    private final EmbeddingProvider fallback = new LocalEmbeddingProvider();

    public GeminiEmbeddingProvider(EmbeddingModel model) {
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        try {
            float[] vector = model.embed(text).content().vector();
            if (vector.length == DIMENSIONS) return vector;
            log.warn("Embedding model returned {} dims, expected {} - using local embedder", vector.length, DIMENSIONS);
        } catch (Exception e) {
            log.warn("Embedding call failed ({}), using local embedder", e.getMessage());
        }
        return fallback.embed(text);
    }
}
