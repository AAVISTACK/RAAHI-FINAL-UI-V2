package in.raahi.backend.repository;

import in.raahi.backend.entity.SosEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SosEventRepository extends JpaRepository<SosEvent, UUID> {

    Optional<SosEvent> findByIdAndUserId(UUID id, UUID userId);

    List<SosEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Fix for the old Node GET /sos/active bug: that endpoint had no radius filter and no
    // role check, so any authenticated user (not just nearby mechanics/helpers) got every
    // active SOS event including the requester's phone number and vehicle reg. This query
    // is radius-bound and the role gate lives in SosController; the DTO built from these
    // rows also withholds phone/vehicle unless the caller is the actual assigned responder.
    @Query(value = """
        SELECT s.* FROM sos_events s
        WHERE s.status = 'ACTIVE'
          AND s.created_at > :since
          AND (
            6371 * acos(
              cos(radians(:lat)) * cos(radians(s.lat)) *
              cos(radians(s.lng) - radians(:lng)) +
              sin(radians(:lat)) * sin(radians(s.lat))
            )
          ) <= :radiusKm
        ORDER BY s.created_at DESC
        LIMIT 20
        """, nativeQuery = true)
    List<SosEvent> findActiveNearby(@Param("lat") double lat, @Param("lng") double lng,
                                     @Param("radiusKm") double radiusKm, @Param("since") Instant since);
}
