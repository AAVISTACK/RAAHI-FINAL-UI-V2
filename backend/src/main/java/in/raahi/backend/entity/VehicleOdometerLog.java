package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only log of odometer updates. Vehicle.odometerKm always holds the current value
 * (what every other read path uses); this table exists purely so "Every update must be
 * persisted" means an actual auditable history, not just an overwritten column.
 */
@Entity
@Table(name = "vehicle_odometer_logs")
public class VehicleOdometerLog {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "odometer_km", nullable = false)
    private Integer odometerKm;

    @Column(name = "recorded_at")
    private Instant recordedAt = Instant.now();

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle v) { this.vehicle = v; }
    public Integer getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Integer v) { this.odometerKm = v; }
    public Instant getRecordedAt() { return recordedAt; }
}
