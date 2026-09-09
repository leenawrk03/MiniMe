package dev.minime.web;

import dev.minime.agent.GeminiScreenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final GeminiScreenService screenService;

    public AgentController(GeminiScreenService screenService) {
        this.screenService = screenService;
    }

    @PostMapping("/observe")
    public Map<String, String> observe(@RequestBody ObserveRequest request) throws Exception {
        try {
            String text = screenService.observe(request.prompt(), request.imageBase64(), request.mimeType());
            return Map.of("text", text, "model", "gemini");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    public record ObserveRequest(String prompt, String imageBase64, String mimeType) {}
}
