package app.atrium.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds {@link TenantContext} from the dev auth headers. {@code /api/**}
 * requires a valid {@code X-Company-Id}; anything else (actuator, root)
 * passes through untenanted.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String COMPANY_HEADER = "X-Company-Id";
    public static final String USER_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CORS preflight (M0.8: browser dashboard calls core-api cross-origin from
        // Vite) never carries the dev headers — this filter runs ahead of Spring's
        // CORS handling in the chain, so an OPTIONS request would otherwise 400
        // before DispatcherServlet ever gets to add the CORS response headers.
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String companyHeader = request.getHeader(COMPANY_HEADER);
        String userHeader = request.getHeader(USER_HEADER);
        // POST /api/v1/companies is the bootstrap call — no tenant exists yet
        boolean companyCreation = "POST".equals(request.getMethod())
                && "/api/v1/companies".equals(request.getRequestURI());
        boolean tenantRequired = request.getRequestURI().startsWith("/api/") && !companyCreation;

        UUID companyId;
        UUID userId;
        try {
            companyId = companyHeader == null ? null : UUID.fromString(companyHeader);
            userId = userHeader == null ? null : UUID.fromString(userHeader);
        } catch (IllegalArgumentException e) {
            reject(response, request, "Malformed UUID in " + COMPANY_HEADER + "/" + USER_HEADER);
            return;
        }

        if (tenantRequired && companyId == null) {
            reject(response, request, "Missing required header " + COMPANY_HEADER);
            return;
        }

        if (companyId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.set(new TenantContext.Tenant(companyId, userId));
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void reject(HttpServletResponse response, HttpServletRequest request, String detail)
            throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Bad Request","status":400,"detail":"%s","instance":"%s"}"""
                .formatted(detail, request.getRequestURI()));
    }
}
