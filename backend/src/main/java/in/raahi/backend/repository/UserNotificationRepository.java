package in.raahi.backend.repository;

import in.raahi.backend.entity.UserNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    @Query("select n from UserNotification n where n.user.id = :userId order by n.createdAt desc")
    List<UserNotification> findRecent(@Param("userId") UUID userId, Pageable pageable);

    @Query("select count(n) from UserNotification n where n.user.id = :userId and n.readAt is null")
    long countUnread(@Param("userId") UUID userId);

    @Modifying
    @Query("update UserNotification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
