package app.atrium.common;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * M3.5, 08 §Security rule 7. {@code application-test.yml} disables the
 * filter by default (every {@code IntegrationTestBase} subclass without its
 * own bean config shares one context+Redis — a tight default limit would
 * throttle unrelated tests' repeated helper calls, the same
 * shared-Testcontainers-Redis reasoning {@code OutboxRelayTest} already
 * established for {@code atrium.relay.interval-ms}). This class's own
 * {@code @TestPropertySource} both re-enables it AND gets its own dedicated
 * Spring context (Spring's context cache keys off property overrides), so
 * its tight limits can never leak into any other test class's requests.
 *
 * <p>Each of the three scopes below uses its own random company/IP/agent
 * key, so the three test methods never share a counter with each other
 * either — except the two pre-auth (IP-scoped) routes, which is why only
 * {@link #ipScopedAuthRoutesShareOneLimitAcrossSignupAndLogin} touches
 * {@code /auth/*} at all; the company-scoped test mints its JWT directly via
 * {@link JwtService} over a JDBC-seeded company row instead of going through
 * {@code /auth/signup}, specifically so it can't consume from that same
 * IP-scoped budget.
 */
@TestPropertySource(properties = {
        "atrium.rate-limit.enabled=true",
        "atrium.rate-limit.default-per-minute=5",
        "atrium.rate-limit.auth-per-minute=3",
        "atrium.rate-limit.worker-per-minute=4"
})
class RateLimitFilterTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate autowiredRest;

    @Autowired
    JwtService jwtService;

    JdbcTemplate jdbc = adminJdbc();

    /**
     * The default {@code TestRestTemplate} bean uses Apache HttpClient5 (added
     * for PATCH support, see pom.xml) — whose {@code DefaultHttpRequestRetryStrategy}
     * automatically retries {@code 429}/{@code 503} responses after honoring
     * {@code Retry-After}, which would silently hide the very rejection this
     * test is trying to observe (the retry lands in the NEXT rate-limit
     * window and "succeeds", masking the 429 entirely). This test needs the
     * raw first response, so it builds its own plain-JDK-backed client instead.
     */
    TestRestTemplate rest;

    @BeforeEach
    void useNonRetryingClient() {
        rest = new TestRestTemplate(new RestTemplateBuilder()
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .rootUri(autowiredRest.getRootUri()));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void companyScopedLimitReturns429WithRetryAfterOnceExceeded() {
        UUID companyId = UUID.randomUUID();
        jdbc.update("INSERT INTO companies (id, name, slug) VALUES (?, ?, ?)",
                companyId, "Rate Limit Co", "ratelimit-" + companyId.toString().substring(0, 8));
        UUID userId = UUID.randomUUID();
        String token = jwtService.issue(userId, companyId, "admin");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> ok = rest.exchange(
                    "/api/v1/companies/" + companyId + "/roster", HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), String.class);
            assertThat(ok.getStatusCode().value()).as("request %d of 5 (the configured limit)", i + 1)
                    .isEqualTo(200);
        }

        ResponseEntity<String> limited = rest.exchange(
                "/api/v1/companies/" + companyId + "/roster", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(limited.getBody()).contains("Too Many Requests");
    }

    @Test
    void ipScopedAuthRoutesShareOneLimitAcrossSignupAndLogin() {
        String email = "ratelimit-" + UUID.randomUUID() + "@test.local";
        // A synthetic per-test X-Forwarded-For (RateLimitFilter.clientIp() prefers it
        // over getRemoteAddr(), exactly as a real load balancer would set it) — gives
        // this test its own fresh ip-scoped bucket every run, so back-to-back reruns
        // within the same 60s fixed window (e.g. re-running this class twice in a row
        // locally) can never collide with a previous run's leftover count the way a
        // fixed loopback address would.
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Forwarded-For", "203.0.113." + (1 + new java.util.Random().nextInt(254)));

        // 1st call: a real signup, consumes 1 of the 3-per-minute auth budget.
        ResponseEntity<String> signup = rest.postForEntity("/api/v1/auth/signup",
                new HttpEntity<>(Map.of(
                        "companyName", "RL Co", "companySlug", "rl-" + UUID.randomUUID().toString().substring(0, 8),
                        "displayName", "Admin", "email", email, "password", "correct-horse-battery"),
                        headers),
                String.class);
        assertThat(signup.getStatusCode().value()).as(signup.getBody()).isEqualTo(201);

        // 2nd + 3rd calls: wrong-password logins still count against the SAME ip-scoped
        // bucket as the signup above (both pre-auth, no tenant bound yet).
        for (int i = 0; i < 2; i++) {
            ResponseEntity<String> login = rest.postForEntity("/api/v1/auth/login",
                    new HttpEntity<>(Map.of("email", email, "password", "wrong"), headers),
                    String.class);
            assertThat(login.getStatusCode().value()).as("login attempt %d", i + 1).isEqualTo(401);
        }

        // 4th call over the 3-per-minute budget: 429, regardless of which auth route.
        ResponseEntity<String> limited = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", "wrong"), headers),
                String.class);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotBlank();
    }

    @Test
    void workerGatewayIsScopedByAgentIdNotSharedAcrossAgents() {
        UUID agentId = UUID.randomUUID();
        UUID randomTaskId = UUID.randomUUID();
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Agent-Id", agentId.toString());

        // The agent doesn't exist, so every call 404s — the rate limiter still counts
        // it (it runs before the controller resolves the agent's company).
        for (int i = 0; i < 4; i++) {
            ResponseEntity<String> notFound = rest.postForEntity(
                    "/api/v1/tasks/" + randomTaskId + "/claim",
                    new HttpEntity<>(headers), String.class);
            assertThat(notFound.getStatusCode().value()).as("claim attempt %d", i + 1).isEqualTo(404);
        }

        ResponseEntity<String> limited = rest.postForEntity(
                "/api/v1/tasks/" + randomTaskId + "/claim",
                new HttpEntity<>(headers), String.class);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);

        // A DIFFERENT agent id is a different scope key — unaffected by the above.
        HttpHeaders otherAgent = jsonHeaders();
        otherAgent.set("X-Agent-Id", UUID.randomUUID().toString());
        ResponseEntity<String> stillOk = rest.postForEntity(
                "/api/v1/tasks/" + randomTaskId + "/claim",
                new HttpEntity<>(otherAgent), String.class);
        assertThat(stillOk.getStatusCode().value()).isEqualTo(404);
    }
}
