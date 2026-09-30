package in.raahi.backend.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.raahi.backend.entity.Job;
import in.raahi.backend.repository.JobRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Event names kept identical to the old Node websocket.js (driver:ping, job:location_update,
 * job:helper_location, job:status_change, sos:nearby_alert) so the Android client's mental
 * model of the protocol doesn't change — only how identity and delivery are handled.
 */
@Component
public class RaahiWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RaahiWebSocketHandler(WebSocketSessionRegistry registry, JobRepository jobRepository) {
        this.registry = registry;
        this.jobRepository = jobRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = userIdOf(session);
        if (userId != null) {
            registry.register(userId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = userIdOf(session);
        if (userId != null) {
            registry.unregister(userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID senderId = userIdOf(session);
        if (senderId == null) return;

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            return; // malformed message, same as the old backend's behavior: ignore
        }

        String event = root.path("event").asText(null);
        if (event == null) return;

        switch (event) {
            case "driver:ping" -> {
                // No standalone driver-location entity exists in this migration yet (the old
                // Node handler was also a no-op stub here: "Store location in memory (or DB
                // for persistence)"). Intentionally left as a no-op rather than inventing a
                // live-location store that doesn't exist in the reference product.
            }
            case "job:location_update" -> handleJobLocationUpdate(senderId, root.path("data"));
            default -> { /* ignore unknown events */ }
        }
    }

    // The old Node handler resolved this by literally broadcasting to every connected client
    // ("For now: broadcast to all"). Here the sender must actually be a participant on the
    // named job, and the update goes only to the *other* participant.
    private void handleJobLocationUpdate(UUID senderId, JsonNode data) {
        String jobIdStr = data.path("job_id").asText(null);
        if (jobIdStr == null) return;

        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) return;
        Job job = jobOpt.get();

        UUID requesterId = job.getRequester().getId();
        UUID helperId = job.getHelper() != null ? job.getHelper().getId() : null;

        boolean senderIsRequester = senderId.equals(requesterId);
        boolean senderIsHelper = helperId != null && senderId.equals(helperId);
        if (!senderIsRequester && !senderIsHelper) {
            return; // sender isn't actually on this job — silently drop, don't relay
        }

        UUID recipientId = senderIsRequester ? helperId : requesterId;
        if (recipientId == null) return;

        double lat = data.path("lat").asDouble();
        double lng = data.path("lng").asDouble();
        registry.sendToUser(recipientId, "job:helper_location",
                Map.of("jobId", jobIdStr, "lat", lat, "lng", lng));
    }

    private UUID userIdOf(WebSocketSession session) {
        Object v = session.getAttributes().get("userId");
        return v instanceof UUID uuid ? uuid : null;
    }
}
