package dev.minime.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.minime.config.MiniMeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Jev (TypeSafe System One) typed-decision client.
 *
 * Jev does not generate text - it answers small typed questions
 * (choice / score / probability) about a piece of state. MiniMe uses it as a
 * fast, cheap intent classifier in front of Gemini: for plain conversation
 * we skip the whole MCP tool specification block, which saves tokens and
 * latency on every turn.
 *
 * The service is optional. With minime.jev.enabled=false or no API key it
 * simply reports "no decision" and ChatService keeps the old behaviour
 * (tools always available).
 *
 * API: POST {base-url}/v1/systemone
 *   { "model": "jev-latest", "state": {...}, "questions": {...} }
 *   -> { "answers": { "<questionId>": { "choice": "...", "confidence": 0.9 } } }
 *
 * Docs: https://docs.typesafe.ai/api
 */
@Service
public class JevDecisionService {

    private static final Logger log = LoggerFactory.getLogger(JevDecisionService.class);

    /** What the user wants MiniMe to do with this message. */
    public enum Intent {
        /** Plain conversation - no tools needed. */
        CHAT,
        /** Wants a macOS application opened. */
        OPEN_APPLICATION,
        /** Wants a GitHub action (issue, PR, repo lookup, ...). */
        GITHUB,
        /** Jev is disabled or could not decide - treat as "unknown". */
        UNKNOWN
    }

    private final MiniMeProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public JevDecisionService(MiniMeProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getJev().getTimeoutSeconds()))
                .build();
    }

    /** True when Jev is configured and should be consulted. */
    public boolean isAvailable() {
        MiniMeProperties.Jev jev = props.getJev();
        return jev.isEnabled()
                && jev.getApiKey() != null
                && !jev.getApiKey().isBlank();
    }

    /**
     * Classifies the user's message. Returns UNKNOWN when Jev is off or the
     * call fails - callers must treat UNKNOWN as "keep the old behaviour".
     */
    public Intent classifyIntent(String userText) {
        if (!isAvailable()) {
            return Intent.UNKNOWN;
        }

        try {
            ObjectNode state = objectMapper.createObjectNode();
            state.put("userMessage", userText);

            ObjectNode criteria = objectMapper.createObjectNode();
            criteria.put("chat",
                    "Plain conversation: questions, small talk, advice, "
                            + "remembering something. No action on the computer "
                            + "or on GitHub is requested.");
            criteria.put("open_application",
                    "The user asks MiniMe to open or launch a macOS "
                            + "application (Notes, Safari, Chrome, Terminal, ...).");
            criteria.put("github",
                    "The user asks for a GitHub action: issues, pull requests, "
                            + "repositories, commits, reviews.");

            ObjectNode question = objectMapper.createObjectNode();
            question.put("instructions",
                    "Classify what the user wants MiniMe to do with this "
                            + "message. Pick exactly one option.");
            question.set("criteria", criteria);

            ObjectNode questions = objectMapper.createObjectNode();
            questions.set("intent", question);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", props.getJev().getModel());
            body.set("state", state);
            body.set("questions", questions);

            JsonNode answers = post(body);
            JsonNode intent = answers == null ? null : answers.path("intent");
            String choice = intent.path("choice").asText("").toLowerCase(Locale.ROOT);
            double confidence = intent.path("confidence").asDouble(0);

            log.debug("Jev intent: {} (confidence {})", choice, confidence);

            return switch (choice) {
                case "chat" -> Intent.CHAT;
                case "open_application" -> Intent.OPEN_APPLICATION;
                case "github" -> Intent.GITHUB;
                default -> Intent.UNKNOWN;
            };
        } catch (Exception e) {
            // Fail open: a Jev outage must never break chatting.
            log.warn("Jev intent classification failed: {}", e.getMessage());
            return Intent.UNKNOWN;
        }
    }

    /**
     * True when Gemini should get the MCP tool specifications for this
     * message. Plain chat skips them (saves tokens); UNKNOWN keeps tools.
     */
    public boolean allowsTools(String userText) {
        return classifyIntent(userText) != Intent.CHAT;
    }

    private JsonNode post(ObjectNode body) throws Exception {
        MiniMeProperties.Jev jev = props.getJev();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jev.getBaseUrl() + "/v1/systemone"))
                .timeout(Duration.ofSeconds(jev.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jev.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            log.warn("Jev answered HTTP {}: {}", response.statusCode(), response.body());
            return null;
        }

        return objectMapper.readTree(response.body()).path("answers");
    }
}
