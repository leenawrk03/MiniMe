package dev.minime.config;

import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.minime.memory.EmbeddingProvider;
import dev.minime.memory.GeminiEmbeddingProvider;
import dev.minime.memory.LocalEmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryConfig.class);

    /**
     * Gemini embeddings when a key is present, otherwise a deterministic local
     * embedder so Phase 2 memory still works fully offline.
     */
    @Bean
    public EmbeddingProvider embeddingProvider(MiniMeProperties props) {
        String key = props.getGemini().getApiKey();
        if (key == null || key.isBlank()) {
            log.warn("No GEMINI_API_KEY - using local hash embeddings for memory.");
            return new LocalEmbeddingProvider();
        }
        log.info("Using Gemini embedding model {}", props.getMemory().getEmbeddingModel());
        return new GeminiEmbeddingProvider(GoogleAiEmbeddingModel.builder()
                .apiKey(key)
                .modelName(props.getMemory().getEmbeddingModel())
                .outputDimensionality(EmbeddingProvider.DIMENSIONS)
                .build());
    }
}
