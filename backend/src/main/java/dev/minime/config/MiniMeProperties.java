package dev.minime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "minime")
public class MiniMeProperties {

    private String conversationId = "primary";
    private String systemPrompt = "You are MiniMe, a helpful personal companion.";
    private Gemini gemini = new Gemini();
    private Memory memory = new Memory();
    private Mcp mcp = new Mcp();

    public static class Gemini {
        private String apiKey = "";
        /** Alias models never get retired, so they are the safe default. */
        private String model = "gemini-3.1-flash-lite";
        /**
         * Tried in order when the primary model answers 503 UNAVAILABLE
         * (overloaded) or 404 NOT_FOUND (model retired).
         */
        private List<String> fallbackModels = new ArrayList<>(Arrays.asList(
                "gemini-3.1-flash-lite",
                "gemini-2.5-flash-lite"
        ));
        private double temperature = 0.8;
        /** Attempts per model before moving on to the next one. */
        private int maxRetries = 1;
        /** First backoff delay in ms; doubles on every retry. */
        private long retryBackoffMs = 400;
        /** Hard timeout per model call - a hung call is worse than a fallback. */
        private int timeoutSeconds = 20;
        /** Caps reply length; shorter replies stream back much faster. */
        private int maxOutputTokens = 512;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<String> getFallbackModels() {
            return fallbackModels;
        }

        public void setFallbackModels(List<String> fallbackModels) {
            this.fallbackModels = fallbackModels;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }
    }

    /** Phase 2: persistent memory tuning. */
    public static class Memory {
        private String embeddingModel = "gemini-embedding-001";
        /** How many facts to inject per turn. */
        private int recallLimit = 6;
        /** Cosine similarity floor for recall. */
        private double minSimilarity = 0.55;
        /** Above this similarity a new fact is treated as a duplicate. */
        private double duplicateSimilarity = 0.93;
        /** Use an extra LLM call to extract facts. Off = fast + no quota burn. */
        private boolean llmExtraction = false;

        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public int getRecallLimit() { return recallLimit; }
        public void setRecallLimit(int recallLimit) { this.recallLimit = recallLimit; }
        public double getMinSimilarity() { return minSimilarity; }
        public void setMinSimilarity(double minSimilarity) { this.minSimilarity = minSimilarity; }
        public boolean isLlmExtraction() { return llmExtraction; }
        public void setLlmExtraction(boolean llmExtraction) { this.llmExtraction = llmExtraction; }
        public double getDuplicateSimilarity() { return duplicateSimilarity; }
        public void setDuplicateSimilarity(double duplicateSimilarity) { this.duplicateSimilarity = duplicateSimilarity; }
    }

    /** MCP client/server wiring. */
    public static class Mcp {
        private GitHub github = new GitHub();

        public GitHub getGithub() { return github; }
        public void setGithub(GitHub github) { this.github = github; }

        /** Client config for the GitHub MCP server (github/github-mcp-server, run via Docker). */
        public static class GitHub {
            /** Off by default: no Docker container listening means no wasted connection attempts. */
            private boolean enabled = false;
            private String endpoint = "http://localhost:8086/mcp";
            /**
             * Personal access token forwarded as a Bearer token to the GitHub MCP
             * server. Only needed if the server itself isn't already configured
             * with GITHUB_PERSONAL_ACCESS_TOKEN.
             */
            private String token = "";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getEndpoint() { return endpoint; }
            public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
            public String getToken() { return token; }
            public void setToken(String token) { this.token = token; }
        }
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Gemini getGemini() { return gemini; }
    public void setGemini(Gemini gemini) { this.gemini = gemini; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }
    public Mcp getMcp() { return mcp; }
    public void setMcp(Mcp mcp) { this.mcp = mcp; }
}
