package dev.minime.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minime.config.MiniMeProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiScreenService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MiniMeProperties props;

    public GeminiScreenService(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            MiniMeProperties props
    ) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public String observe(
            String prompt,
            String imageBase64,
            String mimeType
    ) throws Exception {

        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("Screenshot is empty.");
        }

        String apiKey = props.getGemini().getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not configured."
            );
        }

        String cleanBase64 = imageBase64;

        int comma = cleanBase64.indexOf(',');

        if (cleanBase64.startsWith("data:") && comma >= 0) {
            cleanBase64 = cleanBase64.substring(comma + 1);
        }

        String userRequest =
                prompt == null || prompt.isBlank()
                        ? "What am I looking at?"
                        : prompt.trim();

        String screenInstruction = """
                You are MiniMe's screen-understanding system.

                You are looking at a screenshot of the user's computer.

                Your job is NOT to describe the screenshot like an image captioning
                system. Your job is to understand what the user is asking and use
                the screenshot to give the most useful answer.

                USER'S REQUEST:
                %s

                IMPORTANT RULES:

                1. ANSWER THE USER'S QUESTION FIRST.

                Focus on the meaning and useful content of the screen.

                Do NOT automatically describe:
                - screen dimensions
                - colors
                - borders
                - panels
                - spacing
                - positions
                - generic UI elements
                - every button
                - every piece of visible text

                Only discuss layout or visual appearance if the user specifically
                asks about layout or appearance.

                2. UNDERSTAND THE SCREEN SEMANTICALLY.

                Try to determine:
                - what application or website is visible
                - what page or task the user appears to be on
                - what the page is about
                - the important visible information
                - what the user is currently doing
                - anything unusual or important

                Prefer meaningful interpretation over visual narration.

                3. ERROR AND PROBLEM DETECTION IS IMPORTANT.

                Carefully check the screen for:
                - error messages
                - failed requests
                - exceptions
                - warning messages
                - permission problems
                - login/authentication failures
                - disconnected/offline states
                - broken or missing content
                - crashed applications
                - dialogs requiring attention
                - validation errors
                - obvious malfunctioning UI
                - unexpected states

                If the user asks what is wrong, investigate these things first.

                If there is no obvious problem, say that clearly.

                Do NOT invent a problem just because something looks unusual.

                4. NEVER GUESS AN APPLICATION.

                Do not identify an application as Chrome, Arc, Safari, VS Code,
                Terminal, or anything else merely because the interface resembles it.

                Only name an application when there is actual visible evidence,
                such as:
                - application name
                - recognizable logo
                - distinctive application UI
                - clearly identifiable title/menu information

                If the application cannot be reliably identified, say:
                "I can't reliably identify the application from this screenshot."

                It is better to say that than to guess.

                5. DO NOT INVENT INFORMATION.

                Only state things supported by the screenshot.

                If text is unclear, do not pretend you can read it.

                If the screenshot does not contain enough information to answer
                the question, say so.

                6. READ TEXT SELECTIVELY.

                Do not read every visible word.

                Read text when it is relevant to the user's question, especially:
                - errors
                - warnings
                - titles
                - important messages
                - page headings
                - values
                - labels relevant to the question

                7. KEEP THE ANSWER CONVERSATIONAL.

                MiniMe speaks aloud.

                Give a concise, natural answer rather than a report.

                Usually answer in 1-4 short sentences.

                If there is an important error, include the actual error message
                or the key part of it when it is clearly readable.

                8. WHEN THE USER ASKS "WHAT AM I LOOKING AT?"

                Give a useful high-level explanation of:
                - the application, if reliably identifiable
                - the page/task
                - the most important visible content
                - any obvious problem

                Do not narrate the physical layout of the screen.

                9. WHEN THE USER ASKS "WHAT'S WRONG?" OR SIMILAR

                Prioritize:
                - errors
                - warnings
                - failed operations
                - dialogs
                - broken states

                Start with whether there appears to be a problem.

                10. BE HONEST ABOUT UNCERTAINTY.

                Never convert uncertainty into a confident statement.

                If you are unsure, use language such as:
                "It looks like..."
                "I can see..."
                "I can't reliably tell..."
                "I don't see an obvious error..."

                Return ONLY the natural-language answer that MiniMe should say
                to the user. Do not return JSON, headings, analysis, confidence
                scores, or a description of your reasoning.
                """.formatted(userRequest);

        Map<String, Object> request = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of(
                                                "text",
                                                screenInstruction
                                        ),
                                        Map.of(
                                                "inline_data",
                                                Map.of(
                                                        "mime_type",
                                                        normalizeMimeType(mimeType),
                                                        "data",
                                                        cleanBase64
                                                )
                                        )
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.15,
                        "maxOutputTokens", 500
                )
        );

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("generativelanguage.googleapis.com")
                        .path("/v1beta/models/{model}:generateContent")
                        .build(props.getGemini().getModel()))
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException(
                    "Gemini returned an empty response."
            );
        }

        return extractText(response).trim();
    }

    private String extractText(String response) throws Exception {

        JsonNode root = objectMapper.readTree(response);

        JsonNode candidates = root.path("candidates");

        if (!candidates.isArray() || candidates.isEmpty()) {

            String message = root
                    .path("error")
                    .path("message")
                    .asText("");

            throw new IllegalStateException(
                    message.isBlank()
                            ? "Gemini returned no screen analysis."
                            : message
            );
        }

        StringBuilder out = new StringBuilder();

        for (JsonNode part :
                candidates
                        .get(0)
                        .path("content")
                        .path("parts")) {

            String text = part
                    .path("text")
                    .asText("");

            if (!text.isBlank()) {

                if (!out.isEmpty()) {
                    out.append('\n');
                }

                out.append(text);
            }
        }

        return out.toString();
    }

    private String normalizeMimeType(String mimeType) {

        if (mimeType == null || mimeType.isBlank()) {
            return "image/png";
        }

        return switch (mimeType.toLowerCase()) {

            case "image/png" ->
                    "image/png";

            case "image/jpeg",
                 "image/jpg" ->
                    "image/jpeg";

            case "image/webp" ->
                    "image/webp";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported image MIME type: " + mimeType
                    );
        };
    }
}