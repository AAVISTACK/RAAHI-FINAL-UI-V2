package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.MechanicDtos.*;
import in.raahi.backend.entity.MechanicProfile;
import in.raahi.backend.entity.User;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.MechanicProfileRepository;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/mechanics")
public class MechanicController {

    private final MechanicProfileRepository mechanicRepository;
    private final UserRepository userRepository;

    public MechanicController(MechanicProfileRepository mechanicRepository, UserRepository userRepository) {
        this.mechanicRepository = mechanicRepository;
        this.userRepository = userRepository;
    }

    // No fallback to fake/mock mechanics when this list is empty — the old Node backend did
    // that (_getMockMechanics) and it's explicitly banned. Empty list means empty list.
    @GetMapping("/nearby")
    public ApiResponse<List<MechanicDto>> nearby(
            @RequestParam Double lat, @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "20") Double radius) {

        if (lat == null || lng == null) {
            throw ApiException.badRequest("MISSING_COORDS", "lat and lng required");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ApiException.badRequest("INVALID_COORDS", "lat/lng out of range");
        }

        List<MechanicProfile> profiles = mechanicRepository.findNearby(lat, lng, radius);
        List<MechanicDto> dtos = profiles.stream()
                .map(p -> toDto(p, lat, lng))
                .collect(Collectors.toList());
        return ApiResponse.ok(dtos);
    }

    @GetMapping("/{userId}")
    public ApiResponse<MechanicDto> get(@PathVariable UUID userId) {
        MechanicProfile p = mechanicRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("MECHANIC_NOT_FOUND", "Mechanic not found"));
        return ApiResponse.ok(toDto(p, p.getCurrentLat(), p.getCurrentLng()));
    }

    // Only a MECHANIC/HELPER account updates its own location — enforced via role check,
    // never trusts a userId in the request body.
    @PutMapping("/location")
    public ApiResponse<Object> updateLocation(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody UpdateLocationRequest req) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        if (user.getRole() != User.Role.MECHANIC && user.getRole() != User.Role.HELPER) {
            throw ApiException.forbidden("FORBIDDEN", "Only mechanics/helpers can update service location");
        }

        MechanicProfile profile = mechanicRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    MechanicProfile p = new MechanicProfile();
                    p.setUser(user);
                    return p;
                });
        profile.setCurrentLat(req.lat);
        profile.setCurrentLng(req.lng);
        mechanicRepository.save(profile);

        return ApiResponse.ok(java.util.Map.of("updated", true));
    }

    private MechanicDto toDto(MechanicProfile p, Double refLat, Double refLng) {
        MechanicDto dto = new MechanicDto();
        dto.userId = p.getUser().getId().toString();
        dto.name = p.getUser().getName();
        dto.phone = p.getUser().getPhone();
        dto.shopName = p.getShopName();
        dto.specializations = p.getSpecializations();
        dto.isAvailable = p.isAvailable();
        dto.lat = p.getCurrentLat();
        dto.lng = p.getCurrentLng();
        dto.ratingAvg = p.getUser().getRatingAvg() == null ? 0 : p.getUser().getRatingAvg();
        dto.distanceKm = (refLat != null && refLng != null && p.getCurrentLat() != null)
                ? haversineKm(refLat, refLng, p.getCurrentLat(), p.getCurrentLng())
                : -1;
        return dto;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
