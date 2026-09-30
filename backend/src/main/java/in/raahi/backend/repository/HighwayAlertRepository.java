package in.raahi.backend.repository;

import in.raahi.backend.entity.HighwayAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HighwayAlertRepository extends JpaRepository<HighwayAlert, UUID> {

    @Query("select a from HighwayAlert a where a.expiresAt > :now and (:type is null or a.type = :type) order by a.createdAt desc")
    List<HighwayAlert> findActive(@Param("type") String type, @Param("now") Instant now);
}
