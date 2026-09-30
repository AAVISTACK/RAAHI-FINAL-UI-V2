package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helper_id")
    private User helper;

    @Column(name = "problem_type", nullable = false)
    private String problemType;

    @Column(name = "problem_desc")
    private String problemDesc;

    @Column(name = "req_lat", nullable = false)
    private Double reqLat;

    @Column(name = "req_lng", nullable = false)
    private Double reqLng;

    @Column(name = "highway_name")
    private String highwayName;

    @Column(name = "reward_amount")
    private Double rewardAmount = 0.0;

    @Column(name = "helper_otp")
    private String helperOtp;

    // Closes a brute-force gap found during the Phase 12 security audit: verify-otp had no
    // attempt limit, so an already-assigned helper (the only one who can even call it — see
    // JobController.verifyOtp) could brute-force the 6-digit code via the API instead of
    // actually meeting the requester in person, defeating the point of the handoff check.
    @Column(name = "otp_attempts")
    private int otpAttempts = 0;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum Status { PENDING, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED }

    public UUID getId() { return id; }
    public User getRequester() { return requester; }
    public void setRequester(User v) { this.requester = v; }
    public User getHelper() { return helper; }
    public void setHelper(User v) { this.helper = v; }
    public String getProblemType() { return problemType; }
    public void setProblemType(String v) { this.problemType = v; }
    public String getProblemDesc() { return problemDesc; }
    public void setProblemDesc(String v) { this.problemDesc = v; }
    public Double getReqLat() { return reqLat; }
    public void setReqLat(Double v) { this.reqLat = v; }
    public Double getReqLng() { return reqLng; }
    public void setReqLng(Double v) { this.reqLng = v; }
    public Double getRewardAmount() { return rewardAmount; }
    public void setRewardAmount(Double v) { this.rewardAmount = v; }
    public String getHelperOtp() { return helperOtp; }
    public void setHelperOtp(String v) { this.helperOtp = v; }
    public int getOtpAttempts() { return otpAttempts; }
    public void setOtpAttempts(int v) { this.otpAttempts = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
}
