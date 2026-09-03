package dev.minime.memory;

/** 768-dimension embeddings, matching the vector(768) column. */
public interface EmbeddingProvider {
    int DIMENSIONS = 768;

    float[] embed(String text);
}
