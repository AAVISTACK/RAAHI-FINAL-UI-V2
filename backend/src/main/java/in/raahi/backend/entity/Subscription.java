package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    private Tier tier = Tier.NONE;

    @Enumerated(EnumType.STRING)
    private Status status = Status.INACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum Tier { NONE, BASIC, PRO }
    public enum Status { ACTIVE, INACTIVE }

    public boolean isCurrentlyActive() {
        return status == Status.ACTIVE && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public Tier getTier() { return tier; }
    public void setTier(Tier v) { this.tier = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
}
