package dev.minime.chat;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_message", indexes = @Index(name = "idx_msg_conv_created", columnList = "conversationId, createdAt"))
public class ChatMessageEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String conversationId;

    /** "user" or "assistant". */
    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static ChatMessageEntity of(String conversationId, String role, String content) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.conversationId = conversationId;
        e.role = role;
        e.content = content;
        return e;
    }
}
