package in.raahi.backend.service;

import in.raahi.backend.dto.VehicleDtos.CarHealthDto;
import in.raahi.backend.dto.VehicleDtos.MaintenanceItemDto;
import in.raahi.backend.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Car Health scoring — the ONLY thing that is allowed to produce
 * CarHealthDto.score. No randomness, no per-user hardcoding, no "just show 100".
 *
 * ── THE FORMULA (documented here per the product requirement) ──────────────────────────
 * There are exactly three independently-scored factors. A factor is only scored if the
 * vehicle actually has the data it needs; a factor with no data contributes NEITHER a
 * deduction nor a bonus — it's simply left out, and the response says how many of the 3
 * factors were actually considered (`factorsConsidered`/`factorsTotal`) so the UI can be
 * honest about how complete the picture is.
 *
 * If ZERO of the 3 factors have data, `score` is null and `message` explains why — never a
 * fabricated default number.
 *
 * 1) SERVICE — needs lastServiceDate + lastServiceOdometerKm + current odometerKm.
 *    kmSinceService = odometerKm - lastServiceOdometerKm (floored at 0)
 *    SERVICE_INTERVAL_KM = 5000
 *      <= 5000km since service         → 0 deduction
 *      5001-8000km since service       → -15 (due soon / mildly overdue)
 *      > 8000km since service          → -30 (overdue)
 *
 * 2) INSURANCE — needs insuranceExpiry.
 *    daysToExpiry = insuranceExpiry - today
 *      already expired (daysToExpiry < 0)     → -20
 *      expiring within 30 days                → -10
 *      valid for > 30 days                    → 0
 *
 * 3) PUC — needs pucExpiry. Same shape as insurance, smaller weight (PUC is cheaper/faster
 *    to renew and a shorter-lived document by nature):
 *      already expired          → -15
 *      expiring within 15 days  → -7
 *      valid for > 15 days      → 0
 *
 * Final score = 100 - (sum of deductions from factors that had data), clamped to [0, 100].
 * ─────────────────────────────────────────────────────────────────────────────────────────
 *
 * Maintenance reminders (separate from the score) are generated the same way: each item is
 * only produced when its underlying field is present on the vehicle. Intervals live in the
 * SERVICE_SCHEDULE constants below, not per-user data.
 */
@Service
public class CarHealthService {

    private static final int SERVICE_INTERVAL_KM = 5000;
    private static final int SERVICE_DUE_SOON_KM = 8000; // beyond this, "overdue" not "due soon"
    private static final int TYRE_INTERVAL_KM = 40000;
    private static final int BATTERY_INTERVAL_MONTHS = 30;
    private static final int INSURANCE_DUE_SOON_DAYS = 30;
    private static final int PUC_DUE_SOON_DAYS = 15;

    public CarHealthDto compute(Vehicle v) {
        LocalDate today = LocalDate.now();
        int deduction = 0;
        int factorsConsidered = 0;
        final int factorsTotal = 3;

        // 1) Service factor
        if (v.getLastServiceDate() != null && v.getLastServiceOdometerKm() != null && v.getOdometerKm() != null) {
            factorsConsidered++;
            int kmSince = Math.max(0, v.getOdometerKm() - v.getLastServiceOdometerKm());
            if (kmSince > SERVICE_DUE_SOON_KM) deduction += 30;
            else if (kmSince > SERVICE_INTERVAL_KM) deduction += 15;
        }

        // 2) Insurance factor
        if (v.getInsuranceExpiry() != null) {
            factorsConsidered++;
            long daysToExpiry = ChronoUnit.DAYS.between(today, v.getInsuranceExpiry());
            if (daysToExpiry < 0) deduction += 20;
            else if (daysToExpiry <= INSURANCE_DUE_SOON_DAYS) deduction += 10;
        }

        // 3) PUC factor
        if (v.getPucExpiry() != null) {
            factorsConsidered++;
            long daysToExpiry = ChronoUnit.DAYS.between(today, v.getPucExpiry());
            if (daysToExpiry < 0) deduction += 15;
            else if (daysToExpiry <= PUC_DUE_SOON_DAYS) deduction += 7;
        }

        CarHealthDto dto = new CarHealthDto();
        dto.factorsConsidered = factorsConsidered;
        dto.factorsTotal = factorsTotal;
        dto.maintenanceItems = maintenanceItems(v, today);

        if (factorsConsidered == 0) {
            dto.score = null;
            dto.message = "Complete your vehicle information to calculate your Car Health";
        } else {
            dto.score = Math.max(0, Math.min(100, 100 - deduction));
            dto.message = null;
        }
        return dto;
    }

    private List<MaintenanceItemDto> maintenanceItems(Vehicle v, LocalDate today) {
        List<MaintenanceItemDto> items = new ArrayList<>();

        if (v.getLastServiceDate() != null && v.getLastServiceOdometerKm() != null && v.getOdometerKm() != null) {
            int kmSince = Math.max(0, v.getOdometerKm() - v.getLastServiceOdometerKm());
            int dueAtKm = v.getLastServiceOdometerKm() + SERVICE_INTERVAL_KM;
            MaintenanceItemDto item = new MaintenanceItemDto();
            item.type = "Oil Change";
            item.dueAtKm = dueAtKm;
            if (kmSince > SERVICE_INTERVAL_KM) {
                item.status = "OVERDUE";
                item.detail = "Overdue! Last service at " + v.getLastServiceOdometerKm() + " km, now at " + v.getOdometerKm() + " km (every " + SERVICE_INTERVAL_KM + " km)";
            } else if (kmSince > SERVICE_INTERVAL_KM - 1000) {
                item.status = "DUE_SOON";
                item.detail = (dueAtKm - v.getOdometerKm()) + " km away";
            } else {
                item.status = "OK";
                item.detail = (dueAtKm - v.getOdometerKm()) + " km away";
            }
            items.add(item);
        }

        if (v.getTyreReplacedOdometerKm() != null && v.getOdometerKm() != null) {
            int kmSince = Math.max(0, v.getOdometerKm() - v.getTyreReplacedOdometerKm());
            int dueAtKm = v.getTyreReplacedOdometerKm() + TYRE_INTERVAL_KM;
            MaintenanceItemDto item = new MaintenanceItemDto();
            item.type = "Tyre Rotation";
            item.dueAtKm = dueAtKm;
            item.status = kmSince > TYRE_INTERVAL_KM ? "OVERDUE" : (kmSince > TYRE_INTERVAL_KM - 5000 ? "DUE_SOON" : "OK");
            item.detail = item.status.equals("OVERDUE")
                    ? "Overdue since " + dueAtKm + " km"
                    : (dueAtKm - v.getOdometerKm()) + " km away";
            items.add(item);
        }

        if (v.getBatteryReplacedDate() != null) {
            long monthsSince = ChronoUnit.MONTHS.between(v.getBatteryReplacedDate(), today);
            LocalDate dueDate = v.getBatteryReplacedDate().plusMonths(BATTERY_INTERVAL_MONTHS);
            MaintenanceItemDto item = new MaintenanceItemDto();
            item.type = "Battery Check";
            item.dueAtDate = dueDate.toString();
            item.status = monthsSince >= BATTERY_INTERVAL_MONTHS ? "DUE_SOON" : "OK";
            item.detail = item.status.equals("DUE_SOON")
                    ? "Battery is " + monthsSince + " months old — have it checked"
                    : "Due around " + dueDate;
            items.add(item);
        }

        if (v.getInsuranceExpiry() != null) {
            long daysTo = ChronoUnit.DAYS.between(today, v.getInsuranceExpiry());
            MaintenanceItemDto item = new MaintenanceItemDto();
            item.type = "Insurance Renewal";
            item.dueAtDate = v.getInsuranceExpiry().toString();
            item.status = daysTo < 0 ? "OVERDUE" : (daysTo <= INSURANCE_DUE_SOON_DAYS ? "DUE_SOON" : "OK");
            item.detail = daysTo < 0 ? "Expired " + (-daysTo) + " days ago" : "Expires in " + daysTo + " days";
            items.add(item);
        }

        if (v.getPucExpiry() != null) {
            long daysTo = ChronoUnit.DAYS.between(today, v.getPucExpiry());
            MaintenanceItemDto item = new MaintenanceItemDto();
            item.type = "PUC Renewal";
            item.dueAtDate = v.getPucExpiry().toString();
            item.status = daysTo < 0 ? "OVERDUE" : (daysTo <= PUC_DUE_SOON_DAYS ? "DUE_SOON" : "OK");
            item.detail = daysTo < 0 ? "Expired " + (-daysTo) + " days ago" : "Expires in " + daysTo + " days";
            items.add(item);
        }

        return items;
    }
}
