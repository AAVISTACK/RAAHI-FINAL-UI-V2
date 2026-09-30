package in.raahi.backend.repository;

import in.raahi.backend.entity.Job;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    // GET /jobs/mine originally only covered the requester side, so a MECHANIC/HELPER's own
    // in-progress job (the one they're actually out on) never showed up in "my jobs" at all —
    // an Android Home screen for a helper account would have no way to render an active-job
    // banner. Fixed here rather than left as a client-side workaround, and the controller
    // switched to this query; findByRequesterIdOrderByCreatedAtDesc is left in place in case
    // anything else still depends on the requester-only semantics.
    @Query("select j from Job j where j.requester.id = :userId or j.helper.id = :userId order by j.createdAt desc")
    List<Job> findMine(@Param("userId") UUID userId);

    @Query("select j from Job j where j.status = 'PENDING' and j.expiresAt > :now and j.requester.id <> :excludeUserId order by j.createdAt desc")
    List<Job> findAvailable(@Param("now") Instant now, @Param("excludeUserId") UUID excludeUserId);

    // Row-level lock — this is the fix for the accept-race-condition bug found in the old Node backend
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") UUID id);

    // Helps given: jobs this user completed as the helper. Status is a bind parameter (not a
    // JPQL enum literal) because the package name `in` is a reserved word in JPQL.
    @Query("select count(j) from Job j where j.helper.id = :userId and j.status = :status and j.updatedAt >= :since")
    long countAsHelperSince(@Param("userId") UUID userId, @Param("status") Job.Status status, @Param("since") Instant since);
}
