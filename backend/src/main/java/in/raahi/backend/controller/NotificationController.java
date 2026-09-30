package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.HomeDtos.NotificationDto;
import in.raahi.backend.entity.UserNotification;
import in.raahi.backend.repository.UserNotificationRepository;
import in.raahi.backend.security.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final UserNotificationRepository repository;

    public NotificationController(UserNotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<NotificationDto>> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(repository.findRecent(principal.userId(), PageRequest.of(0, 50))
                .stream().map(this::toDto).collect(Collectors.toList()));
    }

    @PostMapping("/read-all")
    @Transactional
    public ApiResponse<Map<String, Integer>> readAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(Map.of("updated", repository.markAllRead(principal.userId(), Instant.now())));
    }

    private NotificationDto toDto(UserNotification n) {
        NotificationDto d = new NotificationDto();
        d.id = n.getId().toString();
        d.title = n.getTitle();
        d.body = n.getBody();
        d.createdAt = n.getCreatedAt().toString();
        d.read = n.getReadAt() != null;
        return d;
    }
}
