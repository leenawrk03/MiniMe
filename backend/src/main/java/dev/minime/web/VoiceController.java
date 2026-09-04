package dev.minime.web;

import dev.minime.voice.GeminiTranscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

/**
 * Speech-to-text endpoint for the desktop orb.
 *
 * POST /api/voice/transcribe
 * Content-Type: multipart/form-data
 * Field: audio
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final GeminiTranscriptionService transcription;

    public VoiceController(GeminiTranscriptionService transcription) {
        this.transcription = transcription;
    }

    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, String> transcribe(
            @RequestPart("audio") MultipartFile audio) {

        try {
            String text = transcription.transcribe(audio);
            return Map.of(
                    "text", text,
                    "model", "gemini"
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Could not read audio.", e);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Speech transcription failed.", e);
        }
    }
}
