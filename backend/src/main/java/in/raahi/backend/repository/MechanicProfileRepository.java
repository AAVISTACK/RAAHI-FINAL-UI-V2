package in.raahi.backend.repository;

import in.raahi.backend.entity.MechanicProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MechanicProfileRepository extends JpaRepository<MechanicProfile, UUID> {

    Optional<MechanicProfile> findByUserId(UUID userId);

    // Haversine in SQL, same approach as the old Node backend — good enough below ~1000 mechanics.
    // PostGIS upgrade path noted in STATUS.md; NOT adding a mock/random fallback when this is empty.
    @Query(value = """
        SELECT mp.* FROM mechanic_profiles mp
        JOIN users u ON u.id = mp.user_id
        WHERE mp.verification_status = 'APPROVED'
          AND mp.current_lat IS NOT NULL
          AND (
            6371 * acos(
              cos(radians(:lat)) * cos(radians(mp.current_lat)) *
              cos(radians(mp.current_lng) - radians(:lng)) +
              sin(radians(:lat)) * sin(radians(mp.current_lat))
            )
          ) <= :radiusKm
        ORDER BY (
            6371 * acos(
              cos(radians(:lat)) * cos(radians(mp.current_lat)) *
              cos(radians(mp.current_lng) - radians(:lng)) +
              sin(radians(:lat)) * sin(radians(mp.current_lat))
            )
        ) ASC
        LIMIT 20
        """, nativeQuery = true)
    List<MechanicProfile> findNearby(@Param("lat") double lat, @Param("lng") double lng, @Param("radiusKm") double radiusKm);
}
