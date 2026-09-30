package in.raahi.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The old Node backend kept a single global broadcastToAll(event, data) and used it even for
 * per-job location updates ("In production: lookup requester_id from DB / For now: broadcast
 * to all"). That means every connected user — not just the two people on that job — received
 * every helper's live location. This registry intentionally only exposes sendToUser, so a
 * caller has to name exactly who should receive an event.
 */
@Component
public class WebSocketSessionRegistry {

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void register(UUID userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public void unregister(UUID userId) {
        sessions.remove(userId);
    }

    public boolean sendToUser(UUID userId, String event, Object data) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            Map<String, Object> payload = Map.of("event", event, "data", data);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
