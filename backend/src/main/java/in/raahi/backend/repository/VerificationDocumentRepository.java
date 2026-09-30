package in.raahi.backend.repository;

import in.raahi.backend.entity.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {
}
