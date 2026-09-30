package in.raahi.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(
            @Value("${raahi.jwt.secret}") String secret,
            @Value("${raahi.jwt.expiry-days}") long expiryDays) {
        if (secret == null || secret.isBlank()) {
            // Fail loud at startup rather than silently signing tokens with a weak/empty key
            throw new IllegalStateException("JWT_SECRET env var is not set. Refusing to start.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiryMillis = Duration.ofDays(expiryDays).toMillis();
    }

    public String issue(UUID userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMillis))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
