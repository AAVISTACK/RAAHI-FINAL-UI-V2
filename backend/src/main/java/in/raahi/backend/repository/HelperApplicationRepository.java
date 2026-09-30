package in.raahi.backend.repository;

import in.raahi.backend.entity.HelperApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HelperApplicationRepository extends JpaRepository<HelperApplication, UUID> {
    Optional<HelperApplication> findByUserId(UUID userId);
    List<HelperApplication> findByStatusOrderBySubmittedAtAsc(HelperApplication.Status status);
}
