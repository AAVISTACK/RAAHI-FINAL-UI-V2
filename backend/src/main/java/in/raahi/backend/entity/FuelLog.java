package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_logs")
public class FuelLog {
    @Id @GeneratedValue private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "filled_at", nullable = false) private Instant filledAt;
    private BigDecimal litres;
    @Column(name = "price_per_litre") private BigDecimal pricePerLitre;
    @Column(name = "total_cost", nullable = false) private BigDecimal totalCost;
    @Column(name = "odometer_km") private Integer odometerKm;
    @Column(name = "fuel_type") private String fuelType;
    @Column(name = "created_at") private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle v) { this.vehicle = v; }
    public Instant getFilledAt() { return filledAt; }
    public void setFilledAt(Instant v) { this.filledAt = v; }
    public BigDecimal getLitres() { return litres; }
    public void setLitres(BigDecimal v) { this.litres = v; }
    public BigDecimal getPricePerLitre() { return pricePerLitre; }
    public void setPricePerLitre(BigDecimal v) { this.pricePerLitre = v; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal v) { this.totalCost = v; }
    public Integer getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Integer v) { this.odometerKm = v; }
    public String getFuelType() { return fuelType; }
    public void setFuelType(String v) { this.fuelType = v; }
}
