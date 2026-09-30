package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_documents")
public class VerificationDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false)
    private AiStatus aiStatus = AiStatus.UNAVAILABLE;

    @Column(name = "ai_note")
    private String aiNote;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public enum DocType { AADHAAR_FRONT, AADHAAR_BACK, SELFIE }

    // Deliberately never "verified genuine" — the submitted IMAGE only, never a claim about
    // the Aadhaar document's official authenticity (no UIDAI/eKYC integration exists here).
    public enum AiStatus { UNAVAILABLE, LIKELY_UNMODIFIED, POTENTIALLY_MANIPULATED, SUSPICIOUS }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public DocType getDocType() { return docType; }
    public void setDocType(DocType v) { this.docType = v; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] v) { this.content = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public AiStatus getAiStatus() { return aiStatus; }
    public void setAiStatus(AiStatus v) { this.aiStatus = v; }
    public String getAiNote() { return aiNote; }
    public void setAiNote(String v) { this.aiNote = v; }
    public Instant getCreatedAt() { return createdAt; }
}
