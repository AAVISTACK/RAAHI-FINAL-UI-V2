package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A user's own vehicle, entered by them during "Set up your car" onboarding (or later from
 * Profile). One vehicle per user for now — the app's design only ever shows a single
 * "MY CAR" card; multi-vehicle support would be a straightforward extension (drop the
 * unique constraint on user_id) if ever needed, not attempted here.
 *
 * Every field here is either required at setup or explicitly optional — see
 * CarHealthService for how (and whether) each optional field feeds into the health score and
 * maintenance reminders. Nothing on this entity is computed or fabricated; it's exactly what
 * the user entered, persisted as-is.
 */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    private String variant;

    @Column(name = "model_year", nullable = false)
    private Integer modelYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false)
    private FuelType fuelType;

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber;

    @Column(name = "odometer_km", nullable = false)
    private Integer odometerKm;

    @Column(name = "odometer_updated_at")
    private Instant odometerUpdatedAt;

    // --- Optional fields (spec: "do NOT ask for information the backend cannot actually
    // store" is satisfied by having real columns for exactly these and nothing more) ---

    @Column(name = "last_service_date")
    private LocalDate lastServiceDate;

    @Column(name = "last_service_odometer_km")
    private Integer lastServiceOdometerKm;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "puc_expiry")
    private LocalDate pucExpiry;

    @Column(name = "tyre_replaced_date")
    private LocalDate tyreReplacedDate;

    @Column(name = "tyre_replaced_odometer_km")
    private Integer tyreReplacedOdometerKm;

    @Column(name = "battery_replaced_date")
    private LocalDate batteryReplacedDate;

    @Column(name = "battery_replaced_odometer_km")
    private Integer batteryReplacedOdometerKm;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public enum FuelType { PETROL, DIESEL, CNG, ELECTRIC, HYBRID, LPG }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public String getBrand() { return brand; }
    public void setBrand(String v) { this.brand = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public String getVariant() { return variant; }
    public void setVariant(String v) { this.variant = v; }
    public Integer getModelYear() { return modelYear; }
    public void setModelYear(Integer v) { this.modelYear = v; }
    public FuelType getFuelType() { return fuelType; }
    public void setFuelType(FuelType v) { this.fuelType = v; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String v) { this.registrationNumber = v; }
    public Integer getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Integer v) { this.odometerKm = v; }
    public Instant getOdometerUpdatedAt() { return odometerUpdatedAt; }
    public void setOdometerUpdatedAt(Instant v) { this.odometerUpdatedAt = v; }
    public LocalDate getLastServiceDate() { return lastServiceDate; }
    public void setLastServiceDate(LocalDate v) { this.lastServiceDate = v; }
    public Integer getLastServiceOdometerKm() { return lastServiceOdometerKm; }
    public void setLastServiceOdometerKm(Integer v) { this.lastServiceOdometerKm = v; }
    public LocalDate getInsuranceExpiry() { return insuranceExpiry; }
    public void setInsuranceExpiry(LocalDate v) { this.insuranceExpiry = v; }
    public LocalDate getPucExpiry() { return pucExpiry; }
    public void setPucExpiry(LocalDate v) { this.pucExpiry = v; }
    public LocalDate getTyreReplacedDate() { return tyreReplacedDate; }
    public void setTyreReplacedDate(LocalDate v) { this.tyreReplacedDate = v; }
    public Integer getTyreReplacedOdometerKm() { return tyreReplacedOdometerKm; }
    public void setTyreReplacedOdometerKm(Integer v) { this.tyreReplacedOdometerKm = v; }
    public LocalDate getBatteryReplacedDate() { return batteryReplacedDate; }
    public void setBatteryReplacedDate(LocalDate v) { this.batteryReplacedDate = v; }
    public Integer getBatteryReplacedOdometerKm() { return batteryReplacedOdometerKm; }
    public void setBatteryReplacedOdometerKm(Integer v) { this.batteryReplacedOdometerKm = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
