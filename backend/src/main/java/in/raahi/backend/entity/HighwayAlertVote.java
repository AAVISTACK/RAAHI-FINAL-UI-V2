package in.raahi.backend.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "highway_alert_votes")
@IdClass(HighwayAlertVote.Key.class)
public class HighwayAlertVote {

    @Id
    @Column(name = "alert_id")
    private UUID alertId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String vote; // "up" | "down"

    @Column(name = "voted_at")
    private Instant votedAt = Instant.now();

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID v) { this.alertId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public String getVote() { return vote; }
    public void setVote(String v) { this.vote = v; }

    public static class Key implements Serializable {
        private UUID alertId;
        private UUID userId;

        public Key() {}
        public Key(UUID alertId, UUID userId) { this.alertId = alertId; this.userId = userId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(alertId, key.alertId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() { return Objects.hash(alertId, userId); }
    }
}
