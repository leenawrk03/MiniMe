package dev.minime.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minime.chat.ChatMessageEntity;
import dev.minime.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Duplex channel for the orb: the client sends {"text": "..."} and receives
 * state events ("thinking", "speaking"), assistant messages, and — new in
 * Phase 2 — pushed reminder events.
 */
@Component
public class ChatSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatSocketHandler.class);

    private final ChatService chat;
    private final ObjectMapper json = new ObjectMapper();
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public ChatSocketHandler(ChatService chat) {
        this.chat = chat;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<?, ?> payload = json.readValue(message.getPayload(), Map.class);
        Object text = payload.get("text");
        if (text == null || text.toString().isBlank()) return;

        send(session, Map.of("type", "state", "state", "thinking"));
        ChatMessageEntity reply = chat.reply(text.toString());
        send(session, Map.of(
                "type", "message",
                "id", reply.getId().toString(),
                "role", reply.getRole(),
                "content", reply.getContent(),
                "createdAt", reply.getCreatedAt().toString()));
        send(session, Map.of("type", "state", "state", "speaking"));
    }

    /** Fan-out for server-initiated events such as due reminders. */
    public void broadcast(Map<String, Object> payload) {
        for (WebSocketSession session : sessions) {
            try {
                send(session, payload);
            } catch (Exception e) {
                log.warn("Broadcast failed: {}", e.getMessage());
            }
        }
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
        }
    }
}
