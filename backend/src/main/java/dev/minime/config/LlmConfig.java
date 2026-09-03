package dev.minime.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Builds the Gemini chat models.
 *
 * Instead of one hardcoded fallback we build an ordered chain of models
 * (primary first, then every configured fallback). ChatService walks that
 * chain when Google answers with 503 UNAVAILABLE or with 404 NOT_FOUND
 * because a model name was retired.
 */
@Configuration
@EnableConfigurationProperties(MiniMeProperties.class)
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    /** Primary model, kept as the default ChatLanguageModel for other services. */
    @Bean("primaryChatLanguageModel")
    @Primary
    public ChatLanguageModel primaryChatLanguageModel(MiniMeProperties props) {

        String key = props.getGemini().getApiKey();

        if (key == null || key.isBlank()) {
            log.warn("GEMINI_API_KEY is not set - MiniMe will run in offline echo mode.");
            return null;
        }

        String model = props.getGemini().getModel();
        log.info("Using Gemini primary model {}", model);

        return build(key, model, props);
    }

    /**
     * The ordered model chain: primary first, then the configured fallbacks.
     * Empty when no API key is configured.
     */
    @Bean
    public GeminiModelChain geminiModelChain(MiniMeProperties props) {

        String key = props.getGemini().getApiKey();
        List<GeminiModel> chain = new ArrayList<>();

        if (key == null || key.isBlank()) {
            return new GeminiModelChain(chain);
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(props.getGemini().getModel());
        names.addAll(props.getGemini().getFallbackModels());

        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            chain.add(new GeminiModel(name.trim(), build(key, name.trim(), props)));
        }

        log.info("Gemini model chain: {}", names);
        return new GeminiModelChain(chain);
    }

    private ChatLanguageModel build(String key, String modelName, MiniMeProperties props) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(key)
                .modelName(modelName)
                .temperature(props.getGemini().getTemperature())
                .maxOutputTokens(props.getGemini().getMaxOutputTokens())
                .timeout(Duration.ofSeconds(props.getGemini().getTimeoutSeconds()))
                // Retries are handled by ChatService with proper backoff.
                .maxRetries(1)
                .build();
    }

    /** A named Gemini model in the fallback chain. */
    public record GeminiModel(String name, ChatLanguageModel model) {}

    /** Ordered chain of Gemini models: primary first, then fallbacks. */
    public record GeminiModelChain(List<GeminiModel> models) {}
}
