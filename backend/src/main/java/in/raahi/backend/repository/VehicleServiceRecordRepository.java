package in.raahi.backend.repository;

import in.raahi.backend.entity.VehicleServiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleServiceRecordRepository extends JpaRepository<VehicleServiceRecord, UUID> {
    List<VehicleServiceRecord> findByVehicleIdOrderByServiceDateDesc(UUID vehicleId);
}
