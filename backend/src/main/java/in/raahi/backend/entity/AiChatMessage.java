package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_messages")
public class AiChatMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AiChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public enum Role { USER, ASSISTANT }

    public UUID getId() { return id; }
    public AiChatSession getSession() { return session; }
    public void setSession(AiChatSession v) { this.session = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { this.role = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public Instant getCreatedAt() { return createdAt; }
}
