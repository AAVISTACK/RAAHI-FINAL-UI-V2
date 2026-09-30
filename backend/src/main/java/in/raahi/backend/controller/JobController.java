package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.JobDtos.*;
import in.raahi.backend.entity.Job;
import in.raahi.backend.entity.User;
import in.raahi.backend.entity.Subscription;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.notification.NotificationService;
import in.raahi.backend.repository.JobRepository;
import in.raahi.backend.repository.SubscriptionRepository;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.security.AuthenticatedUser;
import in.raahi.backend.websocket.WebSocketSessionRegistry;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WebSocketSessionRegistry sessionRegistry;
    private final NotificationService notificationService;

    public JobController(JobRepository jobRepository, UserRepository userRepository,
                          SubscriptionRepository subscriptionRepository,
                          WebSocketSessionRegistry sessionRegistry,
                          NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.sessionRegistry = sessionRegistry;
        this.notificationService = notificationService;
    }

    // Confirmed from the old Node middleware (requireSubscription): DRIVER accounts need an
    // active subscription to post a job; MECHANIC/HELPER accounts bypass this entirely. This
    // is real existing product logic, not something invented for the migration.
    @PostMapping
    @Transactional
    public ApiResponse<JobDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody CreateJobRequest req) {
        User requester = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        if (requester.getRole() == User.Role.DRIVER) {
            Subscription sub = subscriptionRepository.findByUserId(requester.getId()).orElse(null);
            if (sub == null || !sub.isCurrentlyActive()) {
                throw ApiException.paymentRequired("SUBSCRIPTION_EXPIRED",
                        "Your subscription has expired. Please renew to continue.");
            }
        }

        Job job = new Job();
        job.setRequester(requester);
        job.setProblemType(req.problemType);
        job.setProblemDesc(req.problemDesc);
        job.setReqLat(req.lat);
        job.setReqLng(req.lng);
        job.setRewardAmount(req.rewardAmount == null ? 0.0 : req.rewardAmount);
        job.setHelperOtp(String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999)));
        job.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));

        job = jobRepository.save(job);
        return ApiResponse.ok(toDto(job, requester.getId()));
    }

    @GetMapping
    public ApiResponse<List<JobDto>> available(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<Job> jobs = jobRepository.findAvailable(Instant.now(), principal.userId());
        return ApiResponse.ok(jobs.stream().map(j -> toDto(j, principal.userId())).collect(Collectors.toList()));
    }

    @GetMapping("/mine")
    public ApiResponse<List<JobDto>> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<Job> jobs = jobRepository.findMine(principal.userId());
        return ApiResponse.ok(jobs.stream().map(j -> toDto(j, principal.userId())).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobDto> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Job not found"));
        return ApiResponse.ok(toDto(job, principal.userId()));
    }

    /**
     * THE FIX: the old Node backend did `UPDATE jobs SET ... WHERE status='pending'` with no
     * row lock, so two helpers hitting accept() at nearly the same moment could both read
     * status=pending before either write landed. Here we lock the row first, inside a
     * transaction, so the second caller's check-after-lock reliably sees the first caller's
     * write and gets a clean 409 instead of a race.
     */
    @PostMapping("/{id}/accept")
    @Transactional
    public ApiResponse<JobDto> accept(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        Job job = jobRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Job not found"));

        if (job.getStatus() != Job.Status.PENDING) {
            throw ApiException.conflict("JOB_TAKEN", "Job already taken by someone else");
        }
        if (job.getRequester().getId().equals(principal.userId())) {
            throw ApiException.badRequest("SELF_ACCEPT", "Cannot accept your own job");
        }

        User helper = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        job.setHelper(helper);
        job.setStatus(Job.Status.MATCHED);
        job.setUpdatedAt(Instant.now());
        job = jobRepository.save(job);

        // Targeted push to the requester only — the old Node handler had no equivalent
        // real-time notify here at all (client had to poll); this is additive, and still
        // never a broadcast to unrelated connected users.
        sessionRegistry.sendToUser(job.getRequester().getId(), "job:status_change",
                Map.of("jobId", job.getId().toString(), "status", job.getStatus().name()));
        notificationService.send(job.getRequester(), "Helper found!",
                helper.getName() + " accepted your roadside help request.",
                Map.of("jobId", job.getId().toString(), "type", "job_status_change"));

        return ApiResponse.ok(toDto(job, principal.userId()));
    }

    /**
     * The old Node backend had no equivalent of this at all: its "matched" job just showed
     * the requester an OTP with nothing on the other end to check it against — the helper
     * could tap Complete straight from "matched" with no handoff ever verified (see the fix
     * to complete() below). This is the missing half of the OTP-handoff flow the migration
     * brief calls for: the helper enters what the requester shows them in person, and only a
     * correct match moves the job to IN_PROGRESS.
     */
    @PostMapping("/{id}/verify-otp")
    @Transactional
    public ApiResponse<JobDto> verifyOtp(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable UUID id, @Valid @RequestBody VerifyOtpRequest req) {
        Job job = jobRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Job not found"));

        if (job.getHelper() == null || !job.getHelper().getId().equals(principal.userId())) {
            throw ApiException.forbidden("FORBIDDEN", "Only the assigned helper can verify this OTP");
        }
        if (job.getStatus() != Job.Status.MATCHED) {
            throw ApiException.conflict("INVALID_STATE", "Job is not awaiting OTP handoff");
        }
        if (job.getOtpAttempts() >= 5) {
            throw ApiException.conflict("OTP_LOCKED",
                    "Too many incorrect attempts. Ask the requester to cancel and re-request, or contact support.");
        }
        if (!job.getHelperOtp().equals(req.otp.trim())) {
            job.setOtpAttempts(job.getOtpAttempts() + 1);
            jobRepository.save(job);
            throw ApiException.badRequest("INVALID_OTP", "Incorrect OTP");
        }

        job.setStatus(Job.Status.IN_PROGRESS);
        job.setUpdatedAt(Instant.now());
        job = jobRepository.save(job);

        sessionRegistry.sendToUser(job.getRequester().getId(), "job:status_change",
                Map.of("jobId", job.getId().toString(), "status", job.getStatus().name()));
        notificationService.send(job.getRequester(), "Help is on the way",
                "Your helper has arrived and started the job.",
                Map.of("jobId", job.getId().toString(), "type", "job_status_change"));

        return ApiResponse.ok(toDto(job, principal.userId()));
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public ApiResponse<Object> complete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        Job job = jobRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Job not found"));

        boolean isParticipant = job.getRequester().getId().equals(principal.userId())
                || (job.getHelper() != null && job.getHelper().getId().equals(principal.userId()));
        if (!isParticipant) {
            throw ApiException.forbidden("FORBIDDEN", "Not a participant on this job");
        }
        // The old Node /complete had no status check at all — either participant could mark
        // a job COMPLETED straight from "pending"/"matched", skipping the OTP handoff
        // entirely. Requiring IN_PROGRESS means completion can only happen after
        // verify-otp actually ran.
        if (job.getStatus() != Job.Status.IN_PROGRESS) {
            throw ApiException.conflict("INVALID_STATE", "Job must be in progress (OTP verified) before it can be completed");
        }

        job.setStatus(Job.Status.COMPLETED);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);

        UUID otherParticipant = job.getRequester().getId().equals(principal.userId())
                ? (job.getHelper() != null ? job.getHelper().getId() : null)
                : job.getRequester().getId();
        if (otherParticipant != null) {
            sessionRegistry.sendToUser(otherParticipant, "job:status_change",
                    Map.of("jobId", job.getId().toString(), "status", job.getStatus().name()));
            User otherUser = otherParticipant.equals(job.getRequester().getId()) ? job.getRequester() : job.getHelper();
            notificationService.send(otherUser, "Job completed",
                    "The roadside help job has been marked complete.",
                    Map.of("jobId", job.getId().toString(), "type", "job_status_change"));
        }
        return ApiResponse.ok(java.util.Map.of("success", true));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ApiResponse<Object> cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        Job job = jobRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("JOB_NOT_FOUND", "Job not found"));

        if (!job.getRequester().getId().equals(principal.userId())) {
            throw ApiException.forbidden("FORBIDDEN", "Only the requester can cancel this job");
        }
        if (job.getStatus() == Job.Status.COMPLETED) {
            throw ApiException.conflict("ALREADY_COMPLETED", "Cannot cancel a completed job");
        }

        job.setStatus(Job.Status.CANCELLED);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);

        if (job.getHelper() != null) {
            sessionRegistry.sendToUser(job.getHelper().getId(), "job:status_change",
                    Map.of("jobId", job.getId().toString(), "status", job.getStatus().name()));
            notificationService.send(job.getHelper(), "Job cancelled",
                    "The requester cancelled this roadside help job.",
                    Map.of("jobId", job.getId().toString(), "type", "job_status_change"));
        }
        return ApiResponse.ok(java.util.Map.of("success", true));
    }

    private JobDto toDto(Job j, UUID requestingUserId) {
        JobDto dto = new JobDto();
        dto.id = j.getId().toString();
        dto.status = j.getStatus().name();
        dto.problemType = j.getProblemType();
        dto.problemDesc = j.getProblemDesc();
        dto.lat = j.getReqLat();
        dto.lng = j.getReqLng();
        dto.rewardAmount = j.getRewardAmount();
        dto.requesterName = j.getRequester().getName();
        boolean viewerIsRequester = j.getRequester().getId().equals(requestingUserId);
        dto.viewerRole = viewerIsRequester ? "REQUESTER"
                : (j.getHelper() != null && j.getHelper().getId().equals(requestingUserId) ? "HELPER" : null);
        if (j.getHelper() != null) {
            dto.helperName = j.getHelper().getName();
            dto.helperRatingAvg = j.getHelper().getRatingAvg() == null ? 0 : j.getHelper().getRatingAvg();
            dto.helperTotalHelps = j.getHelper().getTotalHelps() == null ? 0 : j.getHelper().getTotalHelps();
            dto.helperVerified = j.getHelper().isVerified();
            // Phone only visible to the two actual participants — not to every helper browsing the list
            boolean isParticipant = viewerIsRequester || j.getHelper().getId().equals(requestingUserId);
            dto.helperPhone = isParticipant ? j.getHelper().getPhone() : null;
        }
        // requesterPhone is the mirror of helperPhone: only the assigned helper needs it (to
        // coordinate pickup), never a helper still just browsing available jobs. The old Node
        // GET /jobs/:id returned both parties' phone numbers to ANY authenticated caller with
        // no participant check at all — not reproduced here.
        dto.requesterPhone = (j.getHelper() != null && j.getHelper().getId().equals(requestingUserId))
                ? j.getRequester().getPhone() : null;
        // OTP only ever goes to the requester — this is how the helper proves arrival
        dto.helperOtp = viewerIsRequester ? j.getHelperOtp() : null;
        return dto;
    }
}
