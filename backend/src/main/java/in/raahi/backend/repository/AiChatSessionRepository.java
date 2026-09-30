package in.raahi.backend.repository;

import in.raahi.backend.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, UUID> {
    List<AiChatSession> findByUserIdOrderByLastMessageAtDesc(UUID userId);
}
