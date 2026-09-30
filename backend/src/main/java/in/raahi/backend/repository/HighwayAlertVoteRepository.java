package in.raahi.backend.repository;

import in.raahi.backend.entity.HighwayAlertVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HighwayAlertVoteRepository extends JpaRepository<HighwayAlertVote, HighwayAlertVote.Key> {
    Optional<HighwayAlertVote> findByAlertIdAndUserId(UUID alertId, UUID userId);
}
