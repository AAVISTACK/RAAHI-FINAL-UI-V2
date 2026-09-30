package in.raahi.backend.repository;

import in.raahi.backend.entity.VehicleOdometerLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VehicleOdometerLogRepository extends JpaRepository<VehicleOdometerLog, UUID> {

    long countByVehicleId(UUID vehicleId);

    java.util.Optional<VehicleOdometerLog> findFirstByVehicleIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(UUID vehicleId, java.time.Instant at);

    java.util.Optional<VehicleOdometerLog> findFirstByVehicleIdOrderByRecordedAtAsc(UUID vehicleId);
}
