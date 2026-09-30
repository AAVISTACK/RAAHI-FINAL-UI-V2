package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.HomeDtos.CreateFuelLogRequest;
import in.raahi.backend.dto.HomeDtos.FuelLogDto;
import in.raahi.backend.dto.HomeDtos.FuelSummaryDto;
import in.raahi.backend.entity.FuelLog;
import in.raahi.backend.entity.User;
import in.raahi.backend.entity.Vehicle;
import in.raahi.backend.entity.VehicleOdometerLog;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.FuelLogRepository;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.repository.VehicleOdometerLogRepository;
import in.raahi.backend.repository.VehicleRepository;
import in.raahi.backend.security.AuthenticatedUser;
import in.raahi.backend.stats.OdometerRules;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A user's own fuel fill-ups. Identity always comes from the JWT principal — there is no
 * userId in any request or path here, so one user can never read or write another's logs.
 */
@RestController
@RequestMapping("/api/v1/fuel-log")
public class FuelLogController {

    private static final int SUMMARY_DAYS = 7;

    private final FuelLogRepository fuelLogRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleOdometerLogRepository odometerLogRepository;

    public FuelLogController(FuelLogRepository fuelLogRepository, UserRepository userRepository,
                              VehicleRepository vehicleRepository, VehicleOdometerLogRepository odometerLogRepository) {
        this.fuelLogRepository = fuelLogRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.odometerLogRepository = odometerLogRepository;
    }

    @PostMapping
    @Transactional
    public ApiResponse<FuelLogDto> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody CreateFuelLogRequest req) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        // Cost is only ever what the user entered, or litres x price/litre when both were entered.
        BigDecimal total = req.totalCost;
        if (total == null) {
            if (req.litres == null || req.pricePerLitre == null) {
                throw ApiException.badRequest("MISSING_COST", "Enter the total amount, or both litres and price per litre");
            }
            total = req.litres.multiply(req.pricePerLitre).setScale(2, RoundingMode.HALF_UP);
        }

        Instant filledAt = Instant.now();
        if (req.filledAt != null && !req.filledAt.isBlank()) {
            try {
                filledAt = Instant.parse(req.filledAt);
            } catch (DateTimeParseException e) {
                throw ApiException.badRequest("INVALID_FILLED_AT", "filledAt must be an ISO-8601 instant");
            }
            if (filledAt.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
                throw ApiException.badRequest("FILLED_AT_IN_FUTURE", "Fill-up time cannot be in the future");
            }
        }

        Vehicle vehicle = vehicleRepository.findByUserId(user.getId()).orElse(null);

        FuelLog log = new FuelLog();
        log.setUser(user);
        log.setVehicle(vehicle);
        log.setFilledAt(filledAt);
        log.setLitres(req.litres);
        log.setPricePerLitre(req.pricePerLitre);
        log.setTotalCost(total);
        log.setOdometerKm(req.odometerKm);
        String fuelType = req.fuelType == null || req.fuelType.isBlank() ? null : req.fuelType.trim().toUpperCase();
        if (fuelType == null && vehicle != null && vehicle.getFuelType() != null) fuelType = vehicle.getFuelType().name();
        log.setFuelType(fuelType);
        log = fuelLogRepository.save(log);

        // A real odometer reading entered with a fill-up also advances the vehicle's odometer
        // (same rule as service records: only ever forward), which is what Weekly km reads.
        if (vehicle != null && OdometerRules.advances(vehicle.getOdometerKm(), req.odometerKm)) {
            vehicle.setOdometerKm(req.odometerKm);
            vehicle.setOdometerUpdatedAt(Instant.now());
            vehicle.setUpdatedAt(Instant.now());
            vehicleRepository.save(vehicle);

            VehicleOdometerLog odo = new VehicleOdometerLog();
            odo.setVehicle(vehicle);
            odo.setOdometerKm(req.odometerKm);
            odometerLogRepository.save(odo);
        }
        return ApiResponse.ok(toDto(log));
    }

    @GetMapping
    public ApiResponse<List<FuelLogDto>> history(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestParam(defaultValue = "30") int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return ApiResponse.ok(fuelLogRepository.findRecent(principal.userId(), PageRequest.of(0, size))
                .stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/summary")
    public ApiResponse<FuelSummaryDto> summary(@AuthenticationPrincipal AuthenticatedUser principal) {
        Instant since = Instant.now().minus(Duration.ofDays(SUMMARY_DAYS));
        List<FuelLog> logs = fuelLogRepository.findSince(principal.userId(), since);

        FuelSummaryDto dto = new FuelSummaryDto();
        dto.periodDays = SUMMARY_DAYS;
        dto.fillUps = logs.size();
        dto.totalSpent = logs.stream().map(FuelLog::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        boolean allHaveLitres = !logs.isEmpty() && logs.stream().allMatch(l -> l.getLitres() != null);
        dto.totalLitres = allHaveLitres
                ? logs.stream().map(FuelLog::getLitres).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue()
                : null;
        List<FuelLog> latest = fuelLogRepository.findRecent(principal.userId(), PageRequest.of(0, 1));
        dto.lastFilledAt = latest.isEmpty() ? null : latest.get(0).getFilledAt().toString();
        dto.hasAnyLogs = !latest.isEmpty();
        return ApiResponse.ok(dto);
    }

    private FuelLogDto toDto(FuelLog l) {
        FuelLogDto d = new FuelLogDto();
        d.id = l.getId().toString();
        d.filledAt = l.getFilledAt().toString();
        d.litres = l.getLitres() == null ? null : l.getLitres().doubleValue();
        d.pricePerLitre = l.getPricePerLitre() == null ? null : l.getPricePerLitre().doubleValue();
        d.totalCost = l.getTotalCost().doubleValue();
        d.odometerKm = l.getOdometerKm();
        d.fuelType = l.getFuelType();
        return d;
    }
}
