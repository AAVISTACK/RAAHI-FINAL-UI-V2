package in.raahi.backend.controller;

import com.google.firebase.auth.FirebaseToken;
import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.AuthDtos.*;
import in.raahi.backend.entity.Subscription;
import in.raahi.backend.entity.User;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.SubscriptionRepository;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.security.AuthenticatedUser;
import in.raahi.backend.security.FirebaseVerifier;
import in.raahi.backend.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final FirebaseVerifier firebaseVerifier;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JwtService jwtService;

    public AuthController(FirebaseVerifier firebaseVerifier, UserRepository userRepository,
                           SubscriptionRepository subscriptionRepository, JwtService jwtService) {
        this.firebaseVerifier = firebaseVerifier;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jwtService = jwtService;
    }

    // Replaces the old /auth/verifyOtp + /auth/verify-firebase duplicate endpoints.
    @PostMapping("/verify")
    public ApiResponse<VerifyResponse> verify(@Valid @RequestBody VerifyRequest req) {
        FirebaseToken decoded = firebaseVerifier.verify(req.idToken);

        boolean isNewUser = userRepository.findByFirebaseUid(decoded.getUid()).isEmpty();

        User user = userRepository.findByFirebaseUid(decoded.getUid()).orElseGet(() -> {
            User u = new User();
            u.setFirebaseUid(decoded.getUid());
            u.setPhone(decoded.getClaims().get("phone_number") != null
                    ? decoded.getClaims().get("phone_number").toString() : null);
            u.setEmail(decoded.getEmail());
            // SECURITY: client can only ever request DRIVER at signup. MECHANIC/HELPER require
            // a separate application+approval flow; ADMIN is never client-selectable, period.
            u.setRole(User.Role.DRIVER);
            User saved = userRepository.save(u);

            // Mirrors the old Node signup logic: every new DRIVER gets a NONE/INACTIVE
            // subscription row so the 402 gate has something to check against. Mechanics/
            // Helpers don't need one — they're never gated by requireSubscription.
            Subscription sub = new Subscription();
            sub.setUser(saved);
            sub.setTier(Subscription.Tier.NONE);
            sub.setStatus(Subscription.Status.INACTIVE);
            subscriptionRepository.save(sub);

            return saved;
        });

        if (user.getStatus() == User.Status.SUSPENDED) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Account suspended");
        }

        String token = jwtService.issue(user.getId(), user.getRole().name());

        VerifyResponse res = new VerifyResponse();
        res.token = token;
        res.isNewUser = isNewUser;
        res.user = toDto(user);
        return ApiResponse.ok(res);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        return ApiResponse.ok(toDto(user));
    }

    @PutMapping("/profile")
    public ApiResponse<UserDto> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestBody UpdateProfileRequest req) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (req.name != null) user.setName(req.name);
        if (req.vehicleType != null) user.setVehicleType(req.vehicleType);
        if (req.vehicleReg != null) user.setVehicleReg(req.vehicleReg);
        if (req.language != null) user.setLanguage(req.language);
        userRepository.save(user);
        return ApiResponse.ok(toDto(user));
    }

    @PutMapping("/location")
    public ApiResponse<Object> updateLocation(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestBody LocationRequest req) {
        if (req.lat == null || req.lng == null) {
            throw ApiException.badRequest("MISSING_COORDS", "lat and lng required");
        }
        if (req.lat < -90 || req.lat > 90 || req.lng < -180 || req.lng > 180) {
            throw ApiException.badRequest("INVALID_COORDS", "lat/lng out of range");
        }
        // Previously validated input and returned {"updated": true} without persisting
        // anything at all — found during the final audit. Now actually stored on the user
        // row (V9 migration); no other table/feature currently reads this yet (it's distinct
        // from a job's own lat/lng and a mechanic's current_lat/current_lng), so this is
        // general presence data for future use, not wired into any read path yet.
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        user.setLastLat(req.lat);
        user.setLastLng(req.lng);
        user.setLastLocationAt(Instant.now());
        userRepository.save(user);
        return ApiResponse.ok(java.util.Map.of("updated", true));
    }

    @PutMapping("/fcm-token")
    public ApiResponse<Object> updateFcmToken(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestBody FcmTokenRequest req) {
        if (req.token == null || req.token.isBlank()) {
            throw ApiException.badRequest("MISSING_TOKEN", "token is required");
        }
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        user.setFcmToken(req.token);
        userRepository.save(user);
        return ApiResponse.ok(java.util.Map.of("updated", true));
    }

    private UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.id = u.getId().toString();
        dto.name = u.getName();
        dto.phone = u.getPhone();
        dto.role = u.getRole().name();
        dto.vehicleType = u.getVehicleType();
        dto.vehicleReg = u.getVehicleReg();
        dto.isVerified = u.isVerified();
        dto.ratingAvg = u.getRatingAvg() == null ? 0 : u.getRatingAvg();
        dto.totalHelps = u.getTotalHelps() == null ? 0 : u.getTotalHelps();
        return dto;
    }
}
