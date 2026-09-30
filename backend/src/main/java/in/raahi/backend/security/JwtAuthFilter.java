package in.raahi.backend.security;

import in.raahi.backend.entity.User;
import in.raahi.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Extracts our own JWT (issued by AuthController after Firebase verification) from
 * the Authorization header on every request. This is the single source of identity
 * for REST calls — no endpoint should ever trust a client-supplied user id.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                UUID userId = UUID.fromString(claims.getSubject());

                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent() && userOpt.get().getStatus() != User.Status.SUSPENDED) {
                    // Role comes from the DB lookup just above, NOT from the JWT's "role"
                    // claim. The claim is baked in at token issuance and this token can live
                    // for weeks (raahi.jwt.expiry-days) — reading it here would mean a
                    // just-approved Helper stays unable to use Helper-only endpoints, and
                    // worse, a role *downgrade* or suspension-adjacent action wouldn't take
                    // effect until the old token expired. The lookup already happens on
                    // every request for the suspended check, so this is free.
                    AuthenticatedUser principal = new AuthenticatedUser(userId, userOpt.get().getRole().name());
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                // If token is valid but user is suspended/missing, we simply don't authenticate —
                // downstream endpoints requiring auth will correctly return 401/403.
            } catch (Exception ignored) {
                // Invalid/expired token — leave unauthenticated, let Spring Security return 401
            }
        }

        chain.doFilter(request, response);
    }
}
