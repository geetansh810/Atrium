package app.atrium.common;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the session JWT (04 §Auth, M3.1) — HS256, claims
 * {@code sub}=userId, {@code companyId}, {@code role}. Replaces the Phase
 * 0–2 {@code X-Company-Id}/{@code X-User-Id} dev headers entirely.
 */
@Component
public class JwtService {

    /** Decoded token identity — mirrors {@link TenantContext.Tenant} plus role. */
    public record Claims(UUID userId, UUID companyId, String role) {}

    private final Key key;
    private final Duration expiry;
    private final Clock clock;

    public JwtService(@Value("${atrium.jwt.secret}") String secret,
                       @Value("${atrium.jwt.expiry-days:30}") int expiryDays,
                       Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofDays(expiryDays);
        this.clock = clock;
    }

    public String issue(UUID userId, UUID companyId, String role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("companyId", companyId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    /** Empty = missing/expired/malformed/tampered — the caller always maps this to 401. */
    public Optional<Claims> verify(String token) {
        try {
            var jws = Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
                    .parseSignedClaims(token);
            var claims = jws.getPayload();
            return Optional.of(new Claims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("companyId", String.class)),
                    claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
