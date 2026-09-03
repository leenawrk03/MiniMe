package dev.minime.web;

import dev.minime.chat.ChatMessageEntity;
import dev.minime.chat.ChatService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record MessageDto(String id, String role, String content, Instant createdAt) {
        static MessageDto from(ChatMessageEntity e) {
            return new MessageDto(e.getId().toString(), e.getRole(), e.getContent(), e.getCreatedAt());
        }
    }

    public record SendRequest(@NotBlank String text) {}

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/history")
    public List<MessageDto> history() {
        return chat.history().stream().map(MessageDto::from).toList();
    }

    @PostMapping("/message")
    public MessageDto send(@RequestBody SendRequest request) {
        return MessageDto.from(chat.reply(request.text()));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clear() {
        chat.clear();
        return ResponseEntity.noContent().build();
    }
}
