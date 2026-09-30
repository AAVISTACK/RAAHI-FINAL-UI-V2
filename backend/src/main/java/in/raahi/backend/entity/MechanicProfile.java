package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "mechanic_profiles")
public class MechanicProfile {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "specializations")
    private String specializations; // comma-separated for MVP; normalize to a join table later

    @Column(name = "is_available")
    private boolean isAvailable = false;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    public enum VerificationStatus { PENDING, APPROVED, REJECTED }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public String getShopName() { return shopName; }
    public void setShopName(String v) { this.shopName = v; }
    public String getSpecializations() { return specializations; }
    public void setSpecializations(String v) { this.specializations = v; }
    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean v) { this.isAvailable = v; }
    public Double getCurrentLat() { return currentLat; }
    public void setCurrentLat(Double v) { this.currentLat = v; }
    public Double getCurrentLng() { return currentLng; }
    public void setCurrentLng(Double v) { this.currentLng = v; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus v) { this.verificationStatus = v; }
}
