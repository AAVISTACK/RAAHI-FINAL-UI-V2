package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_streaks")
public class DailyStreak {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "current_streak")
    private int currentStreak = 0;

    @Column(name = "longest_streak")
    private int longestStreak = 0;

    @Column(name = "total_checkins")
    private int totalCheckins = 0;

    @Column(name = "last_checkin")
    private LocalDate lastCheckin;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int v) { this.currentStreak = v; }
    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int v) { this.longestStreak = v; }
    public int getTotalCheckins() { return totalCheckins; }
    public void setTotalCheckins(int v) { this.totalCheckins = v; }
    public LocalDate getLastCheckin() { return lastCheckin; }
    public void setLastCheckin(LocalDate v) { this.lastCheckin = v; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
