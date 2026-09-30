package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.DailyDtos.*;
import in.raahi.backend.entity.*;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.places.PlacesProvider;
import in.raahi.backend.repository.*;
import in.raahi.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/daily")
public class DailyController {

    private final DailyStreakRepository streakRepository;
    private final HighwayAlertRepository alertRepository;
    private final HighwayAlertVoteRepository voteRepository;
    private final DailyTipRepository tipRepository;
    private final UserRepository userRepository;
    private final PlacesProvider placesProvider;

    public DailyController(DailyStreakRepository streakRepository, HighwayAlertRepository alertRepository,
                            HighwayAlertVoteRepository voteRepository, DailyTipRepository tipRepository,
                            UserRepository userRepository, PlacesProvider placesProvider) {
        this.streakRepository = streakRepository;
        this.alertRepository = alertRepository;
        this.voteRepository = voteRepository;
        this.tipRepository = tipRepository;
        this.userRepository = userRepository;
        this.placesProvider = placesProvider;
    }

    // ---- Streak ----

    @GetMapping("/streak")
    public ApiResponse<StreakDto> streak(@AuthenticationPrincipal AuthenticatedUser principal) {
        Optional<DailyStreak> existing = streakRepository.findByUserId(principal.userId());
        return ApiResponse.ok(toStreakDto(existing.orElse(null)));
    }

    @PostMapping("/streak/checkin")
    @Transactional
    public ApiResponse<StreakDto> checkin(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        DailyStreak streak = streakRepository.findByUserId(principal.userId()).orElseGet(() -> {
            DailyStreak s = new DailyStreak();
            s.setUser(user);
            return s;
        });

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (today.equals(streak.getLastCheckin())) {
            throw ApiException.conflict("ALREADY_CHECKED_IN", "Already checked in today");
        }

        boolean wasYesterday = streak.getLastCheckin() != null && streak.getLastCheckin().equals(today.minusDays(1));
        int newStreak = wasYesterday ? streak.getCurrentStreak() + 1 : 1;
        streak.setCurrentStreak(newStreak);
        streak.setLongestStreak(Math.max(newStreak, streak.getLongestStreak()));
        streak.setTotalCheckins(streak.getTotalCheckins() + 1);
        streak.setLastCheckin(today);
        streak.setUpdatedAt(Instant.now());
        streakRepository.save(streak);

        StreakDto dto = toStreakDto(streak);
        dto.reward = (newStreak % 7 == 0) ? "7_day_milestone" : null;
        return ApiResponse.ok(dto);
    }

    // ---- Highway Alerts ----

    @GetMapping("/alerts")
    public ApiResponse<List<AlertDto>> alerts(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestParam(required = false) String type) {
        List<HighwayAlert> alerts = alertRepository.findActive(type, Instant.now());
        return ApiResponse.ok(alerts.stream().map(a -> toAlertDto(a, principal.userId())).collect(Collectors.toList()));
    }

    @PostMapping("/alerts")
    @Transactional
    public ApiResponse<AlertDto> createAlert(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @Valid @RequestBody CreateAlertRequest req) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        HighwayAlert alert = new HighwayAlert();
        alert.setUser(user);
        alert.setType(req.type);
        alert.setMessage(req.message);
        alert.setLocation(req.location);
        alert.setLat(req.lat);
        alert.setLng(req.lng);
        alert = alertRepository.save(alert);

        return ApiResponse.ok(toAlertDto(alert, principal.userId()));
    }

    // Fixes a real bug in the old Node backend: POST /daily/alerts/:id/vote had no
    // duplicate-vote check at all, so one user could call it repeatedly and inflate the
    // count without limit. This enforces one vote per (alert, user) via a DB unique
    // constraint (highway_alert_votes) plus this application-level check.
    @PostMapping("/alerts/{id}/vote")
    @Transactional
    public ApiResponse<AlertDto> vote(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID id, @Valid @RequestBody VoteRequest req) {
        if (!req.vote.equals("up") && !req.vote.equals("down")) {
            throw ApiException.badRequest("INVALID_VOTE", "vote must be 'up' or 'down'");
        }
        HighwayAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "Alert not found"));

        if (voteRepository.findByAlertIdAndUserId(id, principal.userId()).isPresent()) {
            throw ApiException.conflict("ALREADY_VOTED", "You already voted on this alert");
        }

        HighwayAlertVote voteRow = new HighwayAlertVote();
        voteRow.setAlertId(id);
        voteRow.setUserId(principal.userId());
        voteRow.setVote(req.vote);
        voteRepository.save(voteRow);

        if (req.vote.equals("up")) alert.setUpvotes(alert.getUpvotes() + 1);
        else alert.setDownvotes(alert.getDownvotes() + 1);
        alertRepository.save(alert);

        return ApiResponse.ok(toAlertDto(alert, principal.userId()));
    }

    // ---- Tips ----

    @GetMapping("/tips")
    public ApiResponse<TipDto> todaysTip() {
        List<DailyTip> tips = tipRepository.findAll();
        if (tips.isEmpty()) {
            throw ApiException.notFound("NO_TIPS", "No tips configured");
        }
        int idx = LocalDate.now(ZoneOffset.UTC).getDayOfYear() % tips.size();
        DailyTip tip = tips.get(idx);
        TipDto dto = new TipDto();
        dto.title = tip.getTitle();
        dto.body = tip.getBody();
        return ApiResponse.ok(dto);
    }

    // ---- Nearby Places (real OSM Overpass data, no fake fallback) ----

    @GetMapping("/places")
    public ApiResponse<List<PlaceDto>> places(@RequestParam Double lat, @RequestParam Double lng,
                                               @RequestParam(defaultValue = "dhaba") String type) {
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ApiException.badRequest("INVALID_COORDS", "Valid lat/lng required");
        }
        try {
            List<PlacesProvider.Place> places = placesProvider.nearby(type, lat, lng, 5.0);
            return ApiResponse.ok(places.stream().map(this::toPlaceDto).collect(Collectors.toList()));
        } catch (PlacesProvider.PlacesUnavailableException e) {
            // Honest failure, not a fabricated place list — the old reference had no backend
            // support for this endpoint at all.
            throw ApiException.serviceUnavailable("PLACES_UNAVAILABLE", "Could not load nearby places right now. Try again shortly.");
        }
    }

    private StreakDto toStreakDto(DailyStreak s) {
        StreakDto dto = new StreakDto();
        if (s == null) {
            dto.currentStreak = 0; dto.longestStreak = 0; dto.totalCheckins = 0;
            dto.lastCheckin = null; dto.canCheckin = true;
            return dto;
        }
        dto.currentStreak = s.getCurrentStreak();
        dto.longestStreak = s.getLongestStreak();
        dto.totalCheckins = s.getTotalCheckins();
        dto.lastCheckin = s.getLastCheckin() != null ? s.getLastCheckin().toString() : null;
        dto.canCheckin = !LocalDate.now(ZoneOffset.UTC).equals(s.getLastCheckin());
        return dto;
    }

    private AlertDto toAlertDto(HighwayAlert a, UUID viewerId) {
        AlertDto dto = new AlertDto();
        dto.id = a.getId().toString();
        dto.type = a.getType();
        dto.message = a.getMessage();
        dto.location = a.getLocation();
        dto.lat = a.getLat();
        dto.lng = a.getLng();
        dto.upvotes = a.getUpvotes();
        dto.downvotes = a.getDownvotes();
        dto.createdAt = a.getCreatedAt().toString();
        dto.postedBy = a.getUser().getName();
        dto.myVote = voteRepository.findByAlertIdAndUserId(a.getId(), viewerId).map(HighwayAlertVote::getVote).orElse(null);
        return dto;
    }

    private PlaceDto toPlaceDto(PlacesProvider.Place p) {
        PlaceDto dto = new PlaceDto();
        dto.id = p.id();
        dto.name = p.name();
        dto.lat = p.lat();
        dto.lng = p.lng();
        dto.distanceKm = p.distanceKm();
        return dto;
    }
}
