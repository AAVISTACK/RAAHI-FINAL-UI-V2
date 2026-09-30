package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.HomeDtos.HomeSummaryDto;
import in.raahi.backend.dto.HomeDtos.WeeklyKmDto;
import in.raahi.backend.entity.Job;
import in.raahi.backend.entity.Vehicle;
import in.raahi.backend.entity.VehicleOdometerLog;
import in.raahi.backend.repository.JobRepository;
import in.raahi.backend.repository.UserNotificationRepository;
import in.raahi.backend.repository.VehicleOdometerLogRepository;
import in.raahi.backend.repository.VehicleRepository;
import in.raahi.backend.security.AuthenticatedUser;
import in.raahi.backend.stats.WeeklyKmCalculator;
import in.raahi.backend.stats.WeeklyKmCalculator.Reading;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Per-user aggregates for Home. All scoped to the JWT principal; nothing here is estimated. */
@RestController
@RequestMapping("/api/v1/home")
public class HomeSummaryController {

    private static final int WINDOW_DAYS = 7;

    private final VehicleRepository vehicleRepository;
    private final VehicleOdometerLogRepository odometerLogRepository;
    private final JobRepository jobRepository;
    private final UserNotificationRepository notificationRepository;

    public HomeSummaryController(VehicleRepository vehicleRepository, VehicleOdometerLogRepository odometerLogRepository,
                                  JobRepository jobRepository, UserNotificationRepository notificationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.odometerLogRepository = odometerLogRepository;
        this.jobRepository = jobRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/summary")
    public ApiResponse<HomeSummaryDto> summary(@AuthenticationPrincipal AuthenticatedUser principal) {
        UUID userId = principal.userId();
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(WINDOW_DAYS));

        HomeSummaryDto dto = new HomeSummaryDto();
        dto.weeklyKm = weeklyKm(userId, now);
        dto.helpsGivenThisWeek = (int) jobRepository.countAsHelperSince(userId, Job.Status.COMPLETED, since);
        dto.unreadNotifications = (int) notificationRepository.countUnread(userId);
        return ApiResponse.ok(dto);
    }

    private WeeklyKmDto weeklyKm(UUID userId, Instant now) {
        WeeklyKmDto out = new WeeklyKmDto();
        Vehicle v = vehicleRepository.findByUserId(userId).orElse(null);
        if (v == null || v.getOdometerKm() == null) {
            out.unavailableReason = "NO_VEHICLE";
            return out;
        }
        Instant cutoff = now.minus(Duration.ofDays(WINDOW_DAYS));
        long count = odometerLogRepository.countByVehicleId(v.getId());
        Reading floor = odometerLogRepository
                .findFirstByVehicleIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(v.getId(), cutoff)
                .map(this::toReading).orElse(null);
        Reading oldest = odometerLogRepository.findFirstByVehicleIdOrderByRecordedAtAsc(v.getId())
                .map(this::toReading).orElse(null);

        WeeklyKmCalculator.Result r = WeeklyKmCalculator.compute(v.getOdometerKm(), count, floor, oldest, now, WINDOW_DAYS);
        out.km = r.km();
        out.since = r.since() == null ? null : r.since().toString();
        out.fullWindow = r.fullWindow();
        out.unavailableReason = r.unavailableReason();
        return out;
    }

    private Reading toReading(VehicleOdometerLog l) {
        return new Reading(l.getRecordedAt(), l.getOdometerKm());
    }
}
