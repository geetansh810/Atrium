package app.atrium.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Redis-backed fixed-window rate limiter for {@code /api/**} (M3.5, 08
 * §Security rule 7). In-process counters would be wrong the moment ECS runs
 * more than one {@code core-api} task (10 §3, 2 tasks min) — each task would
 * grant the same caller its own separate quota — so this counts in the
 * ElastiCache Redis every task already shares (via {@code StringRedisTemplate},
 * a dependency since M0.75's outbox relay).
 *
 * <p>{@code @Order} runs this AFTER {@link TenantContextFilter} so it can read
 * the bound {@link TenantContext} for company-scoped limiting on ordinary
 * authenticated traffic. Three scopes:
 * <ul>
 *   <li>company id — the normal case, once a tenant is bound</li>
 *   <li>{@code X-Agent-Id} — the Worker API gateway's own axis (claim/lease-renew
 *       polling), same header {@link TenantContextFilter} treats specially</li>
 *   <li>client IP — pre-auth routes ({@code /auth/signup}/{@code /auth/login}),
 *       the actual abuse surface since no tenant exists yet to scope by</li>
 * </ul>
 *
 * <p>Over-limit requests get a real {@code 429} + {@code Retry-After}, never a
 * bare connection drop.
 */
@Component
@Order(TenantContextFilter.ORDER + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int defaultPerMinute;
    private final int authPerMinute;
    private final int workerPerMinute;

    public RateLimitFilter(StringRedisTemplate redis,
            @Value("${atrium.rate-limit.enabled:true}") boolean enabled,
            @Value("${atrium.rate-limit.default-per-minute:300}") int defaultPerMinute,
            @Value("${atrium.rate-limit.auth-per-minute:10}") int authPerMinute,
            @Value("${atrium.rate-limit.worker-per-minute:240}") int workerPerMinute) {
        this.redis = redis;
        this.enabled = enabled;
        this.defaultPerMinute = defaultPerMinute;
        this.authPerMinute = authPerMinute;
        this.workerPerMinute = workerPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!enabled || "OPTIONS".equals(request.getMethod()) || !uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Scope scope = resolveScope(request, uri);
        long windowSeconds = WINDOW.getSeconds();
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long windowStart = nowEpochSeconds / windowSeconds;
        String key = "atrium:ratelimit:" + scope.key() + ":" + windowStart;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // +5s grace beyond the window so a slow-arriving request right at
            // the boundary still sees the key before Redis reaps it.
            redis.expire(key, WINDOW.plusSeconds(5));
        }

        if (count != null && count > scope.limit()) {
            long retryAfter = windowSeconds - (nowEpochSeconds % windowSeconds);
            reject(response, request, retryAfter);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Scope resolveScope(HttpServletRequest request, String uri) {
        if (TenantContextFilter.WORKER_GATEWAY.matcher(uri).matches()) {
            String agentId = request.getHeader("X-Agent-Id");
            return new Scope("agent:" + (agentId != null ? agentId : clientIp(request)), workerPerMinute);
        }
        if (TenantContext.isBound()) {
            return new Scope("company:" + TenantContext.requireCompanyId(), defaultPerMinute);
        }
        return new Scope("ip:" + clientIp(request), authPerMinute);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, HttpServletRequest request, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Rate limit exceeded, retry after %d seconds","instance":"%s"}"""
                .formatted(retryAfterSeconds, request.getRequestURI()));
    }

    private record Scope(String key, int limit) {}
}
