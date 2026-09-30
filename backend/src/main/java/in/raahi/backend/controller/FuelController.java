package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.CommerceDtos.FuelRateDto;
import in.raahi.backend.entity.FuelRate;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.FuelRateRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fuel-rates")
public class FuelController {

    private final FuelRateRepository fuelRateRepository;

    public FuelController(FuelRateRepository fuelRateRepository) {
        this.fuelRateRepository = fuelRateRepository;
    }

    @GetMapping
    public ApiResponse<List<FuelRateDto>> all() {
        return ApiResponse.ok(fuelRateRepository.findAll().stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/{state}")
    public ApiResponse<FuelRateDto> byState(@PathVariable String state) {
        FuelRate rate = fuelRateRepository.findById(state)
                .orElseThrow(() -> ApiException.notFound("STATE_NOT_FOUND", "No fuel rate on file for " + state));
        return ApiResponse.ok(toDto(rate));
    }

    // Admin-only manual refresh — the honest replacement for the old Node backend's fake
    // "updated today" stamp: this is the ONLY way updatedAt actually changes, so it always
    // reflects a real edit, never a fabricated live fetch (no real fuel-price API is
    // integrated in this pass).
    @PutMapping("/{state}")
    public ApiResponse<FuelRateDto> update(@AuthenticationPrincipal in.raahi.backend.security.AuthenticatedUser principal,
                                            @PathVariable String state, @Valid @RequestBody UpdateRateRequest req) {
        if (!"ADMIN".equals(principal.role())) {
            throw ApiException.forbidden("FORBIDDEN", "Admin access required");
        }
        FuelRate rate = fuelRateRepository.findById(state).orElseGet(() -> {
            FuelRate r = new FuelRate();
            r.setState(state);
            return r;
        });
        rate.setPetrol(req.petrol);
        rate.setDiesel(req.diesel);
        rate.setCng(req.cng);
        rate.setUpdatedAt(Instant.now());
        rate.setSource("ADMIN_MANUAL");
        fuelRateRepository.save(rate);
        return ApiResponse.ok(toDto(rate));
    }

    public static class UpdateRateRequest {
        @NotNull public BigDecimal petrol;
        @NotNull public BigDecimal diesel;
        public BigDecimal cng;
    }

    private FuelRateDto toDto(FuelRate r) {
        FuelRateDto dto = new FuelRateDto();
        dto.state = r.getState();
        dto.petrol = r.getPetrol().doubleValue();
        dto.diesel = r.getDiesel().doubleValue();
        dto.cng = r.getCng() != null ? r.getCng().doubleValue() : null;
        dto.updatedAt = r.getUpdatedAt().toString();
        dto.source = r.getSource();
        return dto;
    }
}
