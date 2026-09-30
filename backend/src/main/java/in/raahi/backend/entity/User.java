package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true)
    private String firebaseUid;

    @Column(unique = true)
    private String phone;

    private String email;
    private String name;

    @Column(name = "profile_photo")
    private String profilePhoto;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "vehicle_reg")
    private String vehicleReg;

    @Enumerated(EnumType.STRING)
    private Role role = Role.DRIVER;

    @Column(name = "rating_avg")
    private Double ratingAvg = 0.0;

    @Column(name = "total_helps")
    private Integer totalHelps = 0;

    @Column(name = "wallet_balance")
    private Double walletBalance = 0.0;

    private String language = "hi";

    @Column(name = "is_verified")
    private boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lng")
    private Double lastLng;

    @Column(name = "last_location_at")
    private Instant lastLocationAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt = Instant.now();

    public enum Role { DRIVER, MECHANIC, HELPER, ADMIN }
    public enum Status { ACTIVE, SUSPENDED }

    // getters/setters
    public UUID getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String v) { this.firebaseUid = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { this.role = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String v) { this.fcmToken = v; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant v) { this.lastSeenAt = v; }
    public Double getRatingAvg() { return ratingAvg; }
    public Integer getTotalHelps() { return totalHelps; }
    public Double getWalletBalance() { return walletBalance; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String v) { this.vehicleType = v; }
    public String getVehicleReg() { return vehicleReg; }
    public void setVehicleReg(String v) { this.vehicleReg = v; }
    public boolean isVerified() { return isVerified; }
    public String getProfilePhoto() { return profilePhoto; }
    public void setProfilePhoto(String v) { this.profilePhoto = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }
    public Double getLastLat() { return lastLat; }
    public void setLastLat(Double v) { this.lastLat = v; }
    public Double getLastLng() { return lastLng; }
    public void setLastLng(Double v) { this.lastLng = v; }
    public Instant getLastLocationAt() { return lastLocationAt; }
    public void setLastLocationAt(Instant v) { this.lastLocationAt = v; }
}
