package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "helper_applications")
public class HelperApplication {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aadhaar_front_id")
    private VerificationDocument aadhaarFront;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aadhaar_back_id")
    private VerificationDocument aadhaarBack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selfie_id")
    private VerificationDocument selfie;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    public enum Status { PENDING, APPROVED, REJECTED }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public VerificationDocument getAadhaarFront() { return aadhaarFront; }
    public void setAadhaarFront(VerificationDocument v) { this.aadhaarFront = v; }
    public VerificationDocument getAadhaarBack() { return aadhaarBack; }
    public void setAadhaarBack(VerificationDocument v) { this.aadhaarBack = v; }
    public VerificationDocument getSelfie() { return selfie; }
    public void setSelfie(VerificationDocument v) { this.selfie = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String v) { this.rejectionReason = v; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User v) { this.reviewedBy = v; }
}
