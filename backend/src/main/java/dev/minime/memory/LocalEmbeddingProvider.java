package dev.minime.memory;

import java.util.Locale;

/**
 * Offline embedder: hashed bag-of-words projected into 768 dims and L2
 * normalised. Not semantic like a real model, but stable and good enough for
 * lexical recall so MiniMe's memory works without an API key.
 */
public class LocalEmbeddingProvider implements EmbeddingProvider {

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^a-z0-9']+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            int h = token.hashCode();
            int index = Math.floorMod(h, DIMENSIONS);
            vector[index] += (h & 1) == 0 ? 1f : -1f;
            // second hash bucket reduces collisions
            int index2 = Math.floorMod(h * 31 + 7, DIMENSIONS);
            vector[index2] += 0.5f;
        }
        double norm = 0;
        for (float v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm == 0) {
            vector[0] = 1f;
            return vector;
        }
        for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
        return vector;
    }
}
