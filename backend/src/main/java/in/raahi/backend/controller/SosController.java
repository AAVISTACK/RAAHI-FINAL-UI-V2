package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.SosDtos.*;
import in.raahi.backend.entity.MechanicProfile;
import in.raahi.backend.entity.SosEvent;
import in.raahi.backend.entity.User;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.notification.NotificationService;
import in.raahi.backend.repository.MechanicProfileRepository;
import in.raahi.backend.repository.SosEventRepository;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.security.AuthenticatedUser;
import in.raahi.backend.security.RateLimiter;
import in.raahi.backend.websocket.WebSocketSessionRegistry;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sos")
public class SosController {

    private static final double NOTIFY_RADIUS_KM = 15.0; // matches the old Node backend's constant
    private static final int LOOKBACK_HOURS = 2;

    private final SosEventRepository sosEventRepository;
    private final MechanicProfileRepository mechanicRepository;
    private final UserRepository userRepository;
    private final WebSocketSessionRegistry sessionRegistry;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;

    public SosController(SosEventRepository sosEventRepository, MechanicProfileRepository mechanicRepository,
                          UserRepository userRepository, WebSocketSessionRegistry sessionRegistry,
                          NotificationService notificationService, RateLimiter rateLimiter) {
        this.sosEventRepository = sosEventRepository;
        this.mechanicRepository = mechanicRepository;
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
        this.notificationService = notificationService;
        this.rateLimiter = rateLimiter;
    }

    // Bare POST /sos, not /sos/trigger — this was already resolved as the canonical path in
    // API_CONTRACT.md's "Resolved conflicts" section (Flutter's ApiService().triggerSos calls
    // POST /sos; Node's own route was /sos/trigger). Keeping that decision rather than
    // re-litigating it.
    @PostMapping
    @Transactional
    public ApiResponse<TriggerSosResponse> trigger(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody TriggerSosRequest req) {
        // Found unprotected during the security audit: repeated SOS triggers push a real FCM
        // notification + WebSocket alert to every nearby mechanic each time, with zero
        // mitigation — a genuine spam/nuisance vector for other users, not just a load
        // concern. 3 per 10 minutes per user is generous for real emergencies (which don't
        // recur that often) while blocking automated abuse. Fails open if Redis is down.
        boolean allowed = rateLimiter.allow("sos:trigger:" + principal.userId(), 3, Duration.ofMinutes(10));
        if (!allowed) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "Too many SOS triggers. Please wait before trying again.");
        }

        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        SosEvent event = new SosEvent();
        event.setUser(user);
        event.setLat(req.lat);
        event.setLng(req.lng);
        event = sosEventRepository.save(event);

        List<MechanicProfile> nearby = mechanicRepository.findNearby(req.lat, req.lng, NOTIFY_RADIUS_KM);

        // Push a real-time alert to each nearby mechanic/helper individually — never a
        // broadcastToAll (see WebSocketSessionRegistry). Only what a stranger responder needs
        // to decide whether to head over: no phone number, no vehicle reg.
        for (MechanicProfile mp : nearby) {
            sessionRegistry.sendToUser(mp.getUser().getId(), "sos:nearby_alert", Map.of(
                    "sosId", event.getId().toString(),
                    "lat", req.lat,
                    "lng", req.lng
            ));
            // Push notification backstop for a mechanic who isn't actively connected to the
            // WebSocket right now (app backgrounded/killed) — an SOS alert is exactly the
            // case where in-app-only delivery isn't good enough.
            notificationService.send(mp.getUser(), "SOS nearby",
                    "Someone needs emergency roadside help near you.",
                    Map.of("sosId", event.getId().toString(), "type", "sos_nearby_alert"));
        }

        TriggerSosResponse resp = new TriggerSosResponse();
        resp.sos = toDto(event, null, null);
        resp.nearbyMechanicsNotified = nearby.size();
        return ApiResponse.ok(resp);
    }

    // Replaces the old Node GET /sos/active, which returned every active SOS event —
    // including the triggering user's phone number and vehicle registration — to any
    // authenticated caller, with no role check and no radius filter. This version:
    //   1. Only MECHANIC/HELPER accounts may call it (a DRIVER has no reason to see
    //      strangers' emergency locations)
    //   2. Requires the caller's own lat/lng and only returns events within radiusKm
    //   3. Never includes the requester's phone number
    @GetMapping("/active")
    public ApiResponse<List<SosDto>> nearby(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @RequestParam Double lat, @RequestParam Double lng,
                                             @RequestParam(required = false, defaultValue = "15") Double radius) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        if (user.getRole() != User.Role.MECHANIC && user.getRole() != User.Role.HELPER) {
            throw ApiException.forbidden("FORBIDDEN", "Only mechanics/helpers can view nearby SOS alerts");
        }
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ApiException.badRequest("INVALID_COORDS", "Valid lat/lng required");
        }

        Instant since = Instant.now().minus(LOOKBACK_HOURS, ChronoUnit.HOURS);
        List<SosEvent> events = sosEventRepository.findActiveNearby(lat, lng, radius, since);
        return ApiResponse.ok(events.stream().map(e -> toDto(e, lat, lng)).collect(Collectors.toList()));
    }

    @GetMapping("/mine")
    public ApiResponse<List<SosDto>> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<SosEvent> events = sosEventRepository.findByUserIdOrderByCreatedAtDesc(principal.userId());
        return ApiResponse.ok(events.stream().map(e -> toDto(e, null, null)).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/resolve")
    @Transactional
    public ApiResponse<Object> resolve(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        // Ownership-scoped lookup (findByIdAndUserId), not findById + a manual equals check —
        // the old Node query at least filtered by user_id in the WHERE clause, but returned
        // {success:true} even when zero rows matched (id not found, or belonged to someone
        // else) — a caller could never tell resolve actually happened. This 404s honestly.
        SosEvent event = sosEventRepository.findByIdAndUserId(id, principal.userId())
                .orElseThrow(() -> ApiException.notFound("SOS_NOT_FOUND", "SOS event not found"));

        event.setStatus(SosEvent.Status.RESOLVED);
        event.setResolvedAt(Instant.now());
        sosEventRepository.save(event);
        return ApiResponse.ok(Map.of("success", true));
    }

    private SosDto toDto(SosEvent e, Double refLat, Double refLng) {
        SosDto dto = new SosDto();
        dto.id = e.getId().toString();
        dto.status = e.getStatus().name();
        dto.lat = e.getLat();
        dto.lng = e.getLng();
        dto.createdAt = e.getCreatedAt().toString();
        dto.requesterName = e.getUser().getName();
        if (refLat != null && refLng != null) {
            dto.distanceKm = haversineKm(refLat, refLng, e.getLat(), e.getLng());
        }
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
