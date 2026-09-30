package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fuel_rates")
public class FuelRate {

    @Id
    private String state;

    private BigDecimal petrol;
    private BigDecimal diesel;
    private BigDecimal cng;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ADMIN_MANUAL (edited via PUT) or SEED_INDICATIVE (initial migration value, NOT live).
    private String source = "ADMIN_MANUAL";

    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public BigDecimal getPetrol() { return petrol; }
    public void setPetrol(BigDecimal v) { this.petrol = v; }
    public BigDecimal getDiesel() { return diesel; }
    public void setDiesel(BigDecimal v) { this.diesel = v; }
    public BigDecimal getCng() { return cng; }
    public void setCng(BigDecimal v) { this.cng = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
