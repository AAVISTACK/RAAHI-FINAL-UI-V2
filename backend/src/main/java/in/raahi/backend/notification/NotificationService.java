package in.raahi.backend.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import in.raahi.backend.entity.User;
import in.raahi.backend.entity.UserNotification;
import in.raahi.backend.repository.UserNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Reuses the FirebaseApp that FirebaseVerifier already initializes for ID-token verification
 * — that initialization is a hard prerequisite for this backend to start at all (see
 * FirebaseVerifier.init()), so FCM sending here needs no separate credential/config check the
 * way the AI and payment providers do.
 *
 * Real sends only: if a user has no fcmToken on file, or the send call fails, this logs it
 * and returns — callers never see a fabricated "notification sent" result, and nothing here
 * throws back into the request that triggered it (a job accept/SOS trigger should still
 * succeed even if push delivery fails).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserNotificationRepository inboxRepository;

    public NotificationService(UserNotificationRepository inboxRepository) {
        this.inboxRepository = inboxRepository;
    }

    public void send(User user, String title, String body, Map<String, String> data) {
        // Persist to the in-app inbox first so the unread badge reflects every notification
        // addressed to this user, even when they have no FCM token. Never throws to callers.
        try {
            UserNotification n = new UserNotification();
            n.setUser(user);
            n.setTitle(title);
            n.setBody(body);
            inboxRepository.save(n);
        } catch (Exception e) {
            log.warn("Could not persist notification for user {}: {}", user.getId(), e.getMessage());
        }
        String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.debug("No FCM token on file for user {}, skipping push", user.getId());
            return;
        }
        try {
            Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build());
            if (data != null) builder.putAllData(data);
            FirebaseMessaging.getInstance().send(builder.build());
        } catch (FirebaseMessagingException e) {
            // A stale/uninstalled-app token is routine (UNREGISTERED), not something to
            // surface to the caller — logged for now rather than auto-clearing the token,
            // since a periodic cleanup job is a separate concern not built in this pass.
            log.warn("FCM send failed for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
