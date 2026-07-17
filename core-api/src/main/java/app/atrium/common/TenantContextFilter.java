package app.atrium.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds {@link TenantContext} from the session JWT (M3.1 — replaced the dev
 * {@code X-Company-Id}/{@code X-User-Id} headers entirely). {@code /api/**}
 * requires a valid {@code Authorization: Bearer <token>}, except:
 * <ul>
 *   <li>{@code POST /auth/signup}/{@code /auth/login} — the pre-auth bootstrap</li>
 *   <li>the Worker API gateway ({@code /tasks/{id}/claim}, {@code /tasks/{id}/lease/renew})
 *       — a separate axis authenticated by {@code X-Agent-Id} alone (04 §Auth); it
 *       resolves its own tenant from the agent id, never needs a human token</li>
 * </ul>
 *
 * <p>{@code @Order(ORDER)} — must run BEFORE {@link RateLimitFilter} (M3.5,
 * 08 §Security rule 7), which reads the {@link TenantContext} this filter
 * binds to scope its counters by company id.
 */
@Component
@Order(TenantContextFilter.ORDER)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final int ORDER = 10;
    public static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    static final Pattern WORKER_GATEWAY = Pattern.compile(
            "^/api/v1/tasks/[^/]+/(claim|lease/renew)$");

    private final JwtService jwtService;

    public TenantContextFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CORS preflight (M0.8: browser dashboard calls core-api cross-origin
        // from Vite) never carries Authorization — this filter runs ahead of
        // Spring's CORS handling in the chain, so an OPTIONS request would
        // otherwise 401 before DispatcherServlet ever adds the CORS headers.
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        boolean preAuth = "POST".equals(request.getMethod())
                && ("/api/v1/auth/signup".equals(uri) || "/api/v1/auth/login".equals(uri));
        boolean workerGateway = "POST".equals(request.getMethod()) && WORKER_GATEWAY.matcher(uri).matches();
        boolean authRequired = uri.startsWith("/api/") && !preAuth && !workerGateway;

        if (!authRequired) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            reject(response, request, "Missing or malformed " + AUTH_HEADER + " header — expected 'Bearer <token>'");
            return;
        }

        var claims = jwtService.verify(header.substring(BEARER_PREFIX.length()));
        if (claims.isEmpty()) {
            reject(response, request, "Invalid or expired token");
            return;
        }

        try {
            TenantContext.set(new TenantContext.Tenant(
                    claims.get().companyId(), claims.get().userId(), claims.get().role()));
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void reject(HttpServletResponse response, HttpServletRequest request, String detail)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s","instance":"%s"}"""
                .formatted(detail, request.getRequestURI()));
    }
}
