package in.raahi.backend.stats;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure (framework-free) weekly-distance derivation from the persisted odometer history
 * (vehicle_odometer_logs). No estimation: distance is only reported when there are at least
 * two real odometer readings to subtract.
 *
 * baseline = the newest reading at or before (now - windowDays); if none exists (vehicle is
 * newer than the window), the oldest reading — and the result is flagged fullWindow=false
 * with the real `since` instant, so the UI can say "km since 27 Sep" instead of pretending
 * it is a full week.
 */
public final class WeeklyKmCalculator {

    public record Reading(Instant recordedAt, int odometerKm) {}

    public record Result(Integer km, Instant since, boolean fullWindow, String unavailableReason) {}

    private WeeklyKmCalculator() {}

    public static Result compute(int currentOdometerKm, long readingCount,
                                 Reading newestAtOrBeforeCutoff, Reading oldest,
                                 Instant now, int windowDays) {
        if (readingCount < 2 || oldest == null) {
            return new Result(null, null, false, "NEED_MORE_ODOMETER_READINGS");
        }
        Instant cutoff = now.minus(Duration.ofDays(windowDays));
        boolean full = newestAtOrBeforeCutoff != null;
        Reading baseline = full ? newestAtOrBeforeCutoff : oldest;
        int km = Math.max(0, currentOdometerKm - baseline.odometerKm());
        return new Result(km, baseline.recordedAt(), full, null);
    }
}
