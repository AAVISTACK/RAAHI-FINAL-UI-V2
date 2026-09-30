package in.raahi.backend.repository;

import in.raahi.backend.entity.FuelLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FuelLogRepository extends JpaRepository<FuelLog, UUID> {

    @Query("select f from FuelLog f where f.user.id = :userId and f.filledAt >= :since order by f.filledAt desc")
    List<FuelLog> findSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("select f from FuelLog f where f.user.id = :userId order by f.filledAt desc")
    List<FuelLog> findRecent(@Param("userId") UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
