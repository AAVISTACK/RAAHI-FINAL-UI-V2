package in.raahi.backend.repository;

import in.raahi.backend.entity.DailyStreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DailyStreakRepository extends JpaRepository<DailyStreak, UUID> {
    Optional<DailyStreak> findByUserId(UUID userId);
}
