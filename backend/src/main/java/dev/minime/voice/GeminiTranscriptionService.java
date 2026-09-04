package dev.minime.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minime.config.MiniMeProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * Server-side speech-to-text using Gemini audio understanding.
 *
 * The Electron/Angular client only needs to capture microphone audio and POST
 * it as multipart/form-data to /api/voice/transcribe. Browser
 * SpeechRecognition/webkitSpeechRecognition is deliberately not used here.
 */
@Service
public class GeminiTranscriptionService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MiniMeProperties props;

    public GeminiTranscriptionService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            MiniMeProperties props) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public String transcribe(MultipartFile audio) throws IOException {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("Audio file is empty.");
        }

        String apiKey = props.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured.");
        }

        String mimeType = normalizeMimeType(audio.getContentType());
        byte[] bytes = audio.getBytes();

        String model = props.getGemini().getModel();
        String base64 = Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> request = Map.of(
                "contents", java.util.List.of(
                        Map.of(
                                "role", "user",
                                "parts", java.util.List.of(
                                        Map.of(
                                                "text",
                                                """
                                                Transcribe the speech in this audio exactly.
                                                Return ONLY the spoken words as plain text.
                                                Do not add quotes, labels, explanations, markdown,
                                                or commentary. Preserve the language spoken by
                                                the user. If there is no understandable speech,
                                                return an empty string.
                                                """
                                        ),
                                        Map.of(
                                                "inline_data",
                                                Map.of(
                                                        "mime_type", mimeType,
                                                        "data", base64
                                                )
                                        )
                                )
                        )
                )
        );

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("generativelanguage.googleapis.com")
                        .path("/v1beta/models/{model}:generateContent")
                        .build(model))
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response.");
        }

        return extractText(response).trim();
    }

    private String extractText(String response) throws IOException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode candidates = root.path("candidates");

        if (!candidates.isArray() || candidates.isEmpty()) {
            JsonNode error = root.path("error");
            String message = error.path("message").asText("");
            throw new IllegalStateException(
                    message.isBlank()
                            ? "Gemini returned no transcription candidate."
                            : "Gemini error: " + message
            );
        }

        StringBuilder text = new StringBuilder();

        for (JsonNode part : candidates.get(0)
                .path("content")
                .path("parts")) {
            String value = part.path("text").asText("");
            if (!value.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(value);
            }
        }

        return text.toString();
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "audio/webm";
        }

        return switch (mimeType.toLowerCase()) {
            case "audio/webm", "audio/webm;codecs=opus" -> "audio/webm";
            case "audio/wav", "audio/x-wav" -> "audio/wav";
            case "audio/mpeg", "audio/mp3" -> "audio/mpeg";
            case "audio/mp4", "audio/m4a" -> "audio/mp4";
            case "audio/ogg", "audio/ogg;codecs=opus" -> "audio/ogg";
            case "audio/flac" -> "audio/flac";
            case "audio/aac", "audio/x-aac" -> "audio/aac";
            default -> throw new IllegalArgumentException(
                    "Unsupported audio MIME type: " + mimeType
            );
        };
    }
}
