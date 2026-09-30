package in.raahi.backend.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String role) {}
