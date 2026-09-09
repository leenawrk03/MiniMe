package dev.minime.web;

import dev.minime.voice.GeminiTranscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final GeminiTranscriptionService transcriptionService;

    public VoiceController(
            GeminiTranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> transcribe(
            @RequestPart("audio") MultipartFile audio) {

        System.out.println("🎤 /api/voice/transcribe called");
        System.out.println("🎤 File: " + audio.getOriginalFilename());
        System.out.println("🎤 Content type: " + audio.getContentType());
        System.out.println("🎤 Size: " + audio.getSize());

        try {

            String text =
                    transcriptionService.transcribe(audio);

            System.out.println(
                    "✅ Gemini transcription: " + text
            );

            return ResponseEntity.ok(
                    Map.of(
                            "text", text,
                            "model", "gemini"
                    )
            );

        } catch (Exception e) {

            // IMPORTANT:
            // Print the REAL Gemini error.
            System.err.println(
                    "❌ GEMINI TRANSCRIPTION ERROR"
            );

            System.err.println(
                    "❌ Type: " +
                            e.getClass().getName()
            );

            System.err.println(
                    "❌ Message: " +
                            e.getMessage()
            );

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(
                            Map.of(
                                    "error",
                                    "Gemini transcription failed",
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Unknown error"
                            )
                    );
        }
    }
}