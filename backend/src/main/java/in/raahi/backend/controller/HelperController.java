package in.raahi.backend.controller;

import in.raahi.backend.ai.AiTamperScreeningProvider;
import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.HelperDtos.*;
import in.raahi.backend.entity.HelperApplication;
import in.raahi.backend.entity.User;
import in.raahi.backend.entity.VerificationDocument;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.HelperApplicationRepository;
import in.raahi.backend.repository.UserRepository;
import in.raahi.backend.repository.VerificationDocumentRepository;
import in.raahi.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class HelperController {

    private static final long MAX_DOC_BYTES = 8 * 1024 * 1024; // 8MB per image

    private final HelperApplicationRepository applicationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AiTamperScreeningProvider aiTamperScreeningProvider;

    public HelperController(HelperApplicationRepository applicationRepository,
                             VerificationDocumentRepository documentRepository,
                             UserRepository userRepository,
                             AiTamperScreeningProvider aiTamperScreeningProvider) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aiTamperScreeningProvider = aiTamperScreeningProvider;
    }

    // ---- Applicant-facing ----

    @PostMapping(value = "/api/v1/helper/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ApiResponse<HelperApplicationDto> apply(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String email,
            @RequestParam("aadhaarFront") MultipartFile aadhaarFront,
            @RequestParam("aadhaarBack") MultipartFile aadhaarBack,
            @RequestParam(value = "selfie", required = false) MultipartFile selfie
    ) throws IOException {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        if (applicationRepository.findByUserId(user.getId())
                .filter(a -> a.getStatus() == HelperApplication.Status.PENDING).isPresent()) {
            throw ApiException.conflict("APPLICATION_PENDING", "You already have an application under review");
        }

        HelperApplication application = applicationRepository.findByUserId(user.getId()).orElseGet(HelperApplication::new);
        application.setUser(user);
        application.setEmail(email);
        application.setStatus(HelperApplication.Status.PENDING);
        application.setRejectionReason(null);
        application.setReviewedAt(null);
        application.setReviewedBy(null);

        application.setAadhaarFront(storeDocument(user, VerificationDocument.DocType.AADHAAR_FRONT, aadhaarFront));
        application.setAadhaarBack(storeDocument(user, VerificationDocument.DocType.AADHAAR_BACK, aadhaarBack));
        if (selfie != null && !selfie.isEmpty()) {
            application.setSelfie(storeDocument(user, VerificationDocument.DocType.SELFIE, selfie));
        }

        application = applicationRepository.save(application);
        return ApiResponse.ok(toDto(application, false));
    }

    @GetMapping("/api/v1/helper/application/me")
    public ApiResponse<HelperApplicationDto> myApplication(@AuthenticationPrincipal AuthenticatedUser principal) {
        HelperApplication application = applicationRepository.findByUserId(principal.userId())
                .orElseThrow(() -> ApiException.notFound("NO_APPLICATION", "No application submitted yet"));
        return ApiResponse.ok(toDto(application, false));
    }

    // Serves the raw image bytes for one document. Authorized only to the document's owner
    // or an ADMIN reviewing the application — never any other authenticated user. Aadhaar
    // images are exactly the kind of data the migration brief calls out as needing to stay
    // "private and properly authorized."
    @GetMapping("/api/v1/helper/documents/{docId}")
    public ResponseEntity<byte[]> getDocument(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID docId) {
        VerificationDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> ApiException.notFound("DOC_NOT_FOUND", "Document not found"));

        boolean isOwner = doc.getUser().getId().equals(principal.userId());
        boolean isAdmin = "ADMIN".equals(principal.role());
        if (!isOwner && !isAdmin) {
            throw ApiException.forbidden("FORBIDDEN", "Not authorized to view this document");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(doc.getContent());
    }

    // ---- Admin-facing ----
    // Role is read only from the verified JWT's AuthenticatedUser, never trusted from any
    // client-supplied field — ADMIN is server-controlled only (see migration brief), and
    // nothing in this codebase ever assigns it during normal signup.

    @GetMapping("/api/v1/admin/helper-applications")
    public ApiResponse<List<HelperApplicationDto>> queue(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAdmin(principal);
        List<HelperApplication> pending = applicationRepository.findByStatusOrderBySubmittedAtAsc(HelperApplication.Status.PENDING);
        return ApiResponse.ok(pending.stream().map(a -> toDto(a, true)).collect(Collectors.toList()));
    }

    @GetMapping("/api/v1/admin/helper-applications/{id}")
    public ApiResponse<HelperApplicationDto> getForReview(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        requireAdmin(principal);
        HelperApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("APPLICATION_NOT_FOUND", "Application not found"));
        return ApiResponse.ok(toDto(application, true));
    }

    @PostMapping("/api/v1/admin/helper-applications/{id}/approve")
    @Transactional
    public ApiResponse<HelperApplicationDto> approve(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        requireAdmin(principal);
        HelperApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("APPLICATION_NOT_FOUND", "Application not found"));
        if (application.getStatus() != HelperApplication.Status.PENDING) {
            throw ApiException.conflict("ALREADY_REVIEWED", "This application was already reviewed");
        }

        application.setStatus(HelperApplication.Status.APPROVED);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(userRepository.getReferenceById(principal.userId()));
        applicationRepository.save(application);

        User user = application.getUser();
        user.setRole(User.Role.HELPER);
        userRepository.save(user);

        return ApiResponse.ok(toDto(application, true));
    }

    @PostMapping("/api/v1/admin/helper-applications/{id}/reject")
    @Transactional
    public ApiResponse<HelperApplicationDto> reject(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        requireAdmin(principal);
        HelperApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("APPLICATION_NOT_FOUND", "Application not found"));
        if (application.getStatus() != HelperApplication.Status.PENDING) {
            throw ApiException.conflict("ALREADY_REVIEWED", "This application was already reviewed");
        }

        application.setStatus(HelperApplication.Status.REJECTED);
        application.setRejectionReason(req.reason);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(userRepository.getReferenceById(principal.userId()));
        applicationRepository.save(application);

        return ApiResponse.ok(toDto(application, true));
    }

    private void requireAdmin(AuthenticatedUser principal) {
        if (!"ADMIN".equals(principal.role())) {
            throw ApiException.forbidden("FORBIDDEN", "Admin access required");
        }
    }

    private VerificationDocument storeDocument(User user, VerificationDocument.DocType type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("MISSING_DOCUMENT", type + " is required");
        }
        if (file.getSize() > MAX_DOC_BYTES) {
            throw ApiException.badRequest("DOCUMENT_TOO_LARGE", type + " exceeds the 8MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("INVALID_DOCUMENT_TYPE", type + " must be an image");
        }

        byte[] bytes = file.getBytes();
        AiTamperScreeningProvider.ScreeningResult screening = aiTamperScreeningProvider.screen(bytes, contentType);

        VerificationDocument doc = new VerificationDocument();
        doc.setUser(user);
        doc.setDocType(type);
        doc.setContent(bytes);
        doc.setContentType(contentType);
        doc.setAiStatus(screening.status());
        doc.setAiNote(screening.note());
        return documentRepository.save(doc);
    }

    private HelperApplicationDto toDto(HelperApplication a, boolean includeApplicant) {
        HelperApplicationDto dto = new HelperApplicationDto();
        dto.id = a.getId().toString();
        dto.status = a.getStatus().name();
        dto.email = a.getEmail();
        dto.rejectionReason = a.getRejectionReason();
        dto.submittedAt = a.getSubmittedAt() != null ? a.getSubmittedAt().toString() : null;
        dto.reviewedAt = a.getReviewedAt() != null ? a.getReviewedAt().toString() : null;
        dto.aadhaarFront = toDocSummary(a.getAadhaarFront());
        dto.aadhaarBack = toDocSummary(a.getAadhaarBack());
        dto.selfie = toDocSummary(a.getSelfie());
        if (includeApplicant) {
            dto.applicantName = a.getUser().getName();
            dto.applicantPhone = a.getUser().getPhone();
        }
        return dto;
    }

    private DocSummary toDocSummary(VerificationDocument doc) {
        if (doc == null) return null;
        DocSummary s = new DocSummary();
        s.id = doc.getId().toString();
        s.aiStatus = doc.getAiStatus().name();
        s.aiNote = doc.getAiNote();
        return s;
    }
}
