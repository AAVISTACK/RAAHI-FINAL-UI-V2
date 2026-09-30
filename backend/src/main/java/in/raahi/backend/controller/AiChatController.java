package in.raahi.backend.controller;

import in.raahi.backend.ai.AiMechanicProvider;
import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.AiChatDtos.*;
import in.raahi.backend.entity.AiChatMessage;
import in.raahi.backend.entity.AiChatSession;
import in.raahi.backend.entity.User;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.AiChatMessageRepository;
import in.raahi.backend.repository.AiChatSessionRepository;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AiMechanicProvider aiMechanicProvider;

    public AiChatController(AiChatSessionRepository sessionRepository, AiChatMessageRepository messageRepository,
                             UserRepository userRepository, AiMechanicProvider aiMechanicProvider) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.aiMechanicProvider = aiMechanicProvider;
    }

    /** Real availability of the AI provider on this server (credential configured). */
    @GetMapping("/status")
    public ApiResponse<in.raahi.backend.dto.HomeDtos.AiStatusDto> status() {
        in.raahi.backend.dto.HomeDtos.AiStatusDto dto = new in.raahi.backend.dto.HomeDtos.AiStatusDto();
        dto.available = aiMechanicProvider.isConfigured();
        dto.state = dto.available ? "AVAILABLE" : "UNAVAILABLE";
        dto.provider = aiMechanicProvider.providerName();
        dto.message = dto.available ? null : "AI Mechanic is temporarily unavailable";
        return ApiResponse.ok(dto);
    }

    @PostMapping("/sessions")
    @Transactional
    public ApiResponse<SessionDto> createSession(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        AiChatSession session = new AiChatSession();
        session.setUser(user);
        session = sessionRepository.save(session);
        return ApiResponse.ok(toSessionDto(session));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionDto>> mySessions(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<AiChatSession> sessions = sessionRepository.findByUserIdOrderByLastMessageAtDesc(principal.userId());
        return ApiResponse.ok(sessions.stream().map(this::toSessionDto).collect(Collectors.toList()));
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<MessageDto>> messages(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        AiChatSession session = ownedSession(principal, id);
        List<AiChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        return ApiResponse.ok(messages.stream().map(this::toMessageDto).collect(Collectors.toList()));
    }

    @PostMapping("/sessions/{id}/messages")
    @Transactional
    public ApiResponse<MessageDto> sendMessage(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID id, @Valid @RequestBody SendMessageRequest req) {
        AiChatSession session = ownedSession(principal, id);

        AiChatMessage userMessage = new AiChatMessage();
        userMessage.setSession(session);
        userMessage.setRole(AiChatMessage.Role.USER);
        userMessage.setContent(req.content);
        messageRepository.save(userMessage);

        List<AiChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<AiMechanicProvider.ChatTurn> turns = history.stream()
                .map(m -> new AiMechanicProvider.ChatTurn(m.getRole() == AiChatMessage.Role.USER ? "user" : "assistant", m.getContent()))
                .collect(Collectors.toList());

        AiChatMessage assistantMessage = new AiChatMessage();
        assistantMessage.setSession(session);
        assistantMessage.setRole(AiChatMessage.Role.ASSISTANT);
        boolean unavailable;
        try {
            assistantMessage.setContent(aiMechanicProvider.complete(turns));
            unavailable = false;
        } catch (AiMechanicProvider.AiUnavailableException e) {
            // Honest unavailable notice, saved into the transcript as-is — never a fabricated
            // diagnosis standing in for a real answer.
            assistantMessage.setContent("AI Mechanic is currently unavailable. " + e.getMessage()
                    + " Please try Roadside Help or Nearby Mechanics instead.");
            unavailable = true;
        }
        messageRepository.save(assistantMessage);

        session.setLastMessageAt(Instant.now());
        if (session.getTitle() == null) {
            session.setTitle(req.content.length() > 60 ? req.content.substring(0, 60) + "…" : req.content);
        }
        sessionRepository.save(session);

        MessageDto dto = toMessageDto(assistantMessage);
        dto.unavailable = unavailable;
        return ApiResponse.ok(dto);
    }

    // Ownership check lives in exactly one place so every session-scoped endpoint goes
    // through it — the migration brief is explicit that "User A must never access User B's
    // AI sessions/history." 404 (not 403) so a guess at another user's session id doesn't
    // even confirm it exists.
    private AiChatSession ownedSession(AuthenticatedUser principal, UUID sessionId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Session not found"));
        if (!session.getUser().getId().equals(principal.userId())) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session not found");
        }
        return session;
    }

    private SessionDto toSessionDto(AiChatSession s) {
        SessionDto dto = new SessionDto();
        dto.id = s.getId().toString();
        dto.title = s.getTitle();
        dto.createdAt = s.getCreatedAt().toString();
        dto.lastMessageAt = s.getLastMessageAt().toString();
        return dto;
    }

    private MessageDto toMessageDto(AiChatMessage m) {
        MessageDto dto = new MessageDto();
        dto.id = m.getId().toString();
        dto.role = m.getRole().name();
        dto.content = m.getContent();
        dto.createdAt = m.getCreatedAt().toString();
        return dto;
    }
}
