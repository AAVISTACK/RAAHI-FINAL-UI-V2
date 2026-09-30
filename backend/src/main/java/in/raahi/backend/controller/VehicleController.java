package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.VehicleDtos.*;
import in.raahi.backend.entity.User;
import in.raahi.backend.entity.Vehicle;
import in.raahi.backend.entity.VehicleOdometerLog;
import in.raahi.backend.entity.VehicleServiceRecord;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.repository.VehicleOdometerLogRepository;
import in.raahi.backend.repository.VehicleRepository;
import in.raahi.backend.repository.VehicleServiceRecordRepository;
import in.raahi.backend.security.AuthenticatedUser;
import in.raahi.backend.stats.OdometerRules;
import in.raahi.backend.service.CarHealthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A user's own vehicle + service history + computed Car Health. Every read/write here is
 * scoped to `principal.userId()` — the authenticated JWT identity, never a client-supplied
 * id — exactly like MechanicController.updateLocation and every other owner-scoped endpoint
 * in this codebase. There is no way to read or modify another user's vehicle through this
 * controller.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleRepository vehicleRepository;
    private final VehicleServiceRecordRepository serviceRecordRepository;
    private final VehicleOdometerLogRepository odometerLogRepository;
    private final UserRepository userRepository;
    private final CarHealthService carHealthService;

    public VehicleController(VehicleRepository vehicleRepository,
                              VehicleServiceRecordRepository serviceRecordRepository,
                              VehicleOdometerLogRepository odometerLogRepository,
                              UserRepository userRepository,
                              CarHealthService carHealthService) {
        this.vehicleRepository = vehicleRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.odometerLogRepository = odometerLogRepository;
        this.userRepository = userRepository;
        this.carHealthService = carHealthService;
    }

    @GetMapping("/me")
    public ApiResponse<VehicleDto> getMine(@AuthenticationPrincipal AuthenticatedUser principal) {
        Vehicle v = requireVehicle(principal);
        return ApiResponse.ok(toDto(v));
    }

    /** Create-or-update. Used by both the "Set up your car" onboarding step and later edits
     * from Profile — same endpoint, same validation, no separate "create" vs "update" split. */
    @PutMapping("/me")
    @Transactional
    public ApiResponse<VehicleDto> upsert(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody UpsertVehicleRequest req) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        Vehicle.FuelType fuelType = parseFuelType(req.fuelType);

        Vehicle v = vehicleRepository.findByUserId(user.getId()).orElseGet(() -> {
            Vehicle nv = new Vehicle();
            nv.setUser(user);
            return nv;
        });

        // A brand-new vehicle ALWAYS gets its initial odometer reading logged (even 0 km), and an
        // edit can never move the odometer backwards — the same rule PUT /me/odometer enforces.
        boolean isNew = v.getId() == null;
        if (!isNew && OdometerRules.isDecrease(v.getOdometerKm(), req.odometerKm)) {
            throw ApiException.badRequest(
                    "ODOMETER_DECREASED",
                    "New odometer (" + req.odometerKm + " km) is lower than the current reading (" + v.getOdometerKm() + " km)"
            );
        }
        boolean odometerChanged = isNew || v.getOdometerKm() == null || !v.getOdometerKm().equals(req.odometerKm);

        v.setBrand(req.brand.trim());
        v.setModel(req.model.trim());
        v.setVariant(blankToNull(req.variant));
        v.setModelYear(req.modelYear);
        v.setFuelType(fuelType);
        v.setRegistrationNumber(req.registrationNumber.trim().toUpperCase());
        v.setOdometerKm(req.odometerKm);
        v.setLastServiceDate(parseDate(req.lastServiceDate, "lastServiceDate"));
        v.setLastServiceOdometerKm(req.lastServiceOdometerKm);
        v.setInsuranceExpiry(parseDate(req.insuranceExpiry, "insuranceExpiry"));
        v.setPucExpiry(parseDate(req.pucExpiry, "pucExpiry"));
        v.setTyreReplacedDate(parseDate(req.tyreReplacedDate, "tyreReplacedDate"));
        v.setTyreReplacedOdometerKm(req.tyreReplacedOdometerKm);
        v.setBatteryReplacedDate(parseDate(req.batteryReplacedDate, "batteryReplacedDate"));
        v.setBatteryReplacedOdometerKm(req.batteryReplacedOdometerKm);
        v.setUpdatedAt(Instant.now());
        if (odometerChanged) v.setOdometerUpdatedAt(Instant.now());

        v = vehicleRepository.save(v);

        if (odometerChanged) {
            VehicleOdometerLog log = new VehicleOdometerLog();
            log.setVehicle(v);
            log.setOdometerKm(req.odometerKm);
            odometerLogRepository.save(log);
        }

        return ApiResponse.ok(toDto(v));
    }

    @PutMapping("/me/odometer")
    @Transactional
    public ApiResponse<VehicleDto> updateOdometer(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody UpdateOdometerRequest req) {
        Vehicle v = requireVehicle(principal);

        // "Validate that the new odometer value does not move backwards unless an authorized
        // correction flow exists" — no correction flow exists yet, so backward moves are
        // simply rejected rather than silently allowed or fabricated around.
        if (v.getOdometerKm() != null && req.odometerKm < v.getOdometerKm()) {
            throw ApiException.badRequest(
                    "ODOMETER_DECREASED",
                    "New odometer (" + req.odometerKm + " km) is lower than the current reading (" + v.getOdometerKm() + " km)"
            );
        }

        v.setOdometerKm(req.odometerKm);
        v.setOdometerUpdatedAt(Instant.now());
        v.setUpdatedAt(Instant.now());
        vehicleRepository.save(v);

        VehicleOdometerLog log = new VehicleOdometerLog();
        log.setVehicle(v);
        log.setOdometerKm(req.odometerKm);
        odometerLogRepository.save(log);

        return ApiResponse.ok(toDto(v));
    }

    @GetMapping("/me/health")
    public ApiResponse<CarHealthDto> health(@AuthenticationPrincipal AuthenticatedUser principal) {
        Vehicle v = requireVehicle(principal);
        return ApiResponse.ok(carHealthService.compute(v));
    }

    @GetMapping("/me/service-records")
    public ApiResponse<List<ServiceRecordDto>> serviceRecords(@AuthenticationPrincipal AuthenticatedUser principal) {
        Vehicle v = requireVehicle(principal);
        List<ServiceRecordDto> dtos = serviceRecordRepository.findByVehicleIdOrderByServiceDateDesc(v.getId())
                .stream().map(this::toDto).collect(Collectors.toList());
        return ApiResponse.ok(dtos);
    }

    /** Adding a record also advances the vehicle's current odometer if the record's reading
     * is higher than what's on file — a real observed reading from the user, not a guess. */
    @PostMapping("/me/service-records")
    @Transactional
    public ApiResponse<ServiceRecordDto> addServiceRecord(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @Valid @RequestBody CreateServiceRecordRequest req) {
        Vehicle v = requireVehicle(principal);

        VehicleServiceRecord record = new VehicleServiceRecord();
        record.setVehicle(v);
        record.setServiceDate(parseDate(req.serviceDate, "serviceDate"));
        record.setOdometerKm(req.odometerKm);
        record.setServiceType(req.serviceType.trim());
        record.setNotes(blankToNull(req.notes));
        record.setCost(req.cost);
        record.setWorkshopName(blankToNull(req.workshopName));
        record.setPartsReplaced(blankToNull(req.partsReplaced));
        record = serviceRecordRepository.save(record);

        if (OdometerRules.advances(v.getOdometerKm(), req.odometerKm)) {
            v.setOdometerKm(req.odometerKm);
            v.setOdometerUpdatedAt(Instant.now());
            v.setUpdatedAt(Instant.now());
            vehicleRepository.save(v);

            VehicleOdometerLog log = new VehicleOdometerLog();
            log.setVehicle(v);
            log.setOdometerKm(req.odometerKm);
            odometerLogRepository.save(log);
        }

        // A logged general/oil service also counts as the vehicle's "last service" for
        // scoring/reminders purposes if it's the most recent one on file.
        if ("Oil Change".equalsIgnoreCase(record.getServiceType()) || "General Service".equalsIgnoreCase(record.getServiceType())) {
            if (v.getLastServiceDate() == null || !record.getServiceDate().isBefore(v.getLastServiceDate())) {
                v.setLastServiceDate(record.getServiceDate());
                v.setLastServiceOdometerKm(record.getOdometerKm());
                vehicleRepository.save(v);
            }
        }

        return ApiResponse.ok(toDto(record));
    }

    private Vehicle requireVehicle(AuthenticatedUser principal) {
        return vehicleRepository.findByUserId(principal.userId())
                .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "No vehicle set up yet"));
    }

    private Vehicle.FuelType parseFuelType(String raw) {
        try {
            return Vehicle.FuelType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_FUEL_TYPE", "fuelType must be one of PETROL, DIESEL, CNG, ELECTRIC, HYBRID, LPG");
        }
    }

    private LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_DATE", field + " must be an ISO date (yyyy-MM-dd)");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private VehicleDto toDto(Vehicle v) {
        VehicleDto dto = new VehicleDto();
        dto.id = v.getId().toString();
        dto.brand = v.getBrand();
        dto.model = v.getModel();
        dto.variant = v.getVariant();
        dto.modelYear = v.getModelYear();
        dto.fuelType = v.getFuelType() == null ? null : v.getFuelType().name();
        dto.registrationNumber = v.getRegistrationNumber();
        dto.odometerKm = v.getOdometerKm();
        dto.odometerUpdatedAt = v.getOdometerUpdatedAt() == null ? null : v.getOdometerUpdatedAt().toString();
        dto.lastServiceDate = v.getLastServiceDate() == null ? null : v.getLastServiceDate().toString();
        dto.lastServiceOdometerKm = v.getLastServiceOdometerKm();
        dto.insuranceExpiry = v.getInsuranceExpiry() == null ? null : v.getInsuranceExpiry().toString();
        dto.pucExpiry = v.getPucExpiry() == null ? null : v.getPucExpiry().toString();
        dto.tyreReplacedDate = v.getTyreReplacedDate() == null ? null : v.getTyreReplacedDate().toString();
        dto.tyreReplacedOdometerKm = v.getTyreReplacedOdometerKm();
        dto.batteryReplacedDate = v.getBatteryReplacedDate() == null ? null : v.getBatteryReplacedDate().toString();
        dto.batteryReplacedOdometerKm = v.getBatteryReplacedOdometerKm();
        dto.createdAt = v.getCreatedAt() == null ? null : v.getCreatedAt().toString();
        dto.updatedAt = v.getUpdatedAt() == null ? null : v.getUpdatedAt().toString();
        return dto;
    }

    private ServiceRecordDto toDto(VehicleServiceRecord r) {
        ServiceRecordDto dto = new ServiceRecordDto();
        dto.id = r.getId().toString();
        dto.serviceDate = r.getServiceDate().toString();
        dto.odometerKm = r.getOdometerKm();
        dto.serviceType = r.getServiceType();
        dto.notes = r.getNotes();
        dto.cost = r.getCost();
        dto.workshopName = r.getWorkshopName();
        dto.partsReplaced = r.getPartsReplaced();
        dto.createdAt = r.getCreatedAt() == null ? null : r.getCreatedAt().toString();
        return dto;
    }
}
