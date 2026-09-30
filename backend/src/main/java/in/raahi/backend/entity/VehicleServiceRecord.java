package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One real service/repair event the user logged against their own vehicle. This is the
 * foundation for future, better-informed Car Health calculations (per the product decision:
 * "this history becomes the foundation for future Car Health calculations") — CarHealthService
 * doesn't read this table yet (v1 uses Vehicle's own last-service/insurance/PUC fields), but
 * the table and API exist now so that data starts accumulating from day one.
 */
@Entity
@Table(name = "vehicle_service_records")
public class VehicleServiceRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "odometer_km", nullable = false)
    private Integer odometerKm;

    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Double cost;

    @Column(name = "workshop_name")
    private String workshopName;

    @Column(name = "parts_replaced", columnDefinition = "TEXT")
    private String partsReplaced;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle v) { this.vehicle = v; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate v) { this.serviceDate = v; }
    public Integer getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Integer v) { this.odometerKm = v; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String v) { this.serviceType = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public Double getCost() { return cost; }
    public void setCost(Double v) { this.cost = v; }
    public String getWorkshopName() { return workshopName; }
    public void setWorkshopName(String v) { this.workshopName = v; }
    public String getPartsReplaced() { return partsReplaced; }
    public void setPartsReplaced(String v) { this.partsReplaced = v; }
    public Instant getCreatedAt() { return createdAt; }
}
