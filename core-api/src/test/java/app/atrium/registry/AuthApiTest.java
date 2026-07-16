package app.atrium.registry;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * M3.1 Done-when: fresh browser → signup → empty roster, unassisted; dev
 * headers ({@code X-Company-Id}/{@code X-User-Id}) are rejected entirely —
 * only {@code Authorization: Bearer <JWT>} works.
 */
class AuthApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Unparseable response: " + body, e);
        }
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

    private ResponseEntity<String> signup(String slug, String email, String password) {
        return rest.postForEntity("/api/v1/auth/signup",
                new HttpEntity<>(Map.of(
                        "companyName", "Co " + slug, "companySlug", slug,
                        "displayName", "Admin", "email", email, "password", password),
                        jsonHeaders()),
                String.class);
    }

    private ResponseEntity<String> login(String email, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders()),
                String.class);
    }

    // ── Done-when: fresh signup → empty roster, unassisted ──────────────────

    @Test
    void signupCreatesCompanyAndAdmin_rosterStartsEmpty() {
        String slug = "fresh-" + UUID.randomUUID().toString().substring(0, 8);
        String email = slug + "@test.local";
        ResponseEntity<String> response = signup(slug, email, "correct-horse-battery");
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);

        JsonNode body = parse(response.getBody());
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("companySlug").asText()).isEqualTo(slug);
        assertThat(body.get("companyName").asText()).isEqualTo("Co " + slug);
        assertThat(body.get("displayName").asText()).isEqualTo("Admin");
        assertThat(body.get("role").asText()).isEqualTo("admin");
        String companyId = body.get("companyId").asText();
        String token = body.get("token").asText();

        ResponseEntity<String> roster = rest.exchange(
                "/api/v1/companies/" + companyId + "/roster", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);
        assertThat(roster.getStatusCode().value()).isEqualTo(200);
        assertThat(parse(roster.getBody())).isEmpty();
    }

    @Test
    void duplicateSlugAndDuplicateEmailAreRejected() {
        String slug = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        String email = slug + "@test.local";
        assertThat(signup(slug, email, "correct-horse-battery").getStatusCode().value()).isEqualTo(201);

        // same slug, different email → 409
        assertThat(signup(slug, "other-" + email, "correct-horse-battery")
                .getStatusCode().value()).isEqualTo(409);

        // same email, different slug → 409
        ResponseEntity<String> dupEmail = signup("dup2-" + UUID.randomUUID().toString().substring(0, 8),
                email, "correct-horse-battery");
        assertThat(dupEmail.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void signupValidatesInputs() {
        String slug = "bad-" + UUID.randomUUID().toString().substring(0, 8);
        // password too short
        ResponseEntity<String> shortPw = signup(slug, slug + "@test.local", "short");
        assertThat(shortPw.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(shortPw.getBody()).get("fieldErrors").has("password")).isTrue();

        // malformed email
        ResponseEntity<String> badEmail = signup(slug, "not-an-email", "correct-horse-battery");
        assertThat(badEmail.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(badEmail.getBody()).get("fieldErrors").has("email")).isTrue();

        // bad slug pattern
        ResponseEntity<String> badSlug = rest.postForEntity("/api/v1/auth/signup",
                new HttpEntity<>(Map.of("companyName", "X", "companySlug", "Not Valid!",
                        "displayName", "Admin", "email", slug + "@test.local", "password", "correct-horse-battery"),
                        jsonHeaders()),
                String.class);
        assertThat(badSlug.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(badSlug.getBody()).get("fieldErrors").has("companySlug")).isTrue();
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    void loginSucceedsWithCorrectCredentialsAndFailsOtherwise() {
        String slug = "login-" + UUID.randomUUID().toString().substring(0, 8);
        String email = slug + "@test.local";
        ResponseEntity<String> signedUp = signup(slug, email, "correct-horse-battery");
        String companyId = parse(signedUp.getBody()).get("companyId").asText();

        ResponseEntity<String> ok = login(email, "correct-horse-battery");
        assertThat(ok.getStatusCode().value()).as(ok.getBody()).isEqualTo(200);
        assertThat(parse(ok.getBody()).get("companyId").asText()).isEqualTo(companyId);
        assertThat(parse(ok.getBody()).get("token").asText()).isNotBlank();

        assertThat(login(email, "wrong-password").getStatusCode().value()).isEqualTo(401);
        assertThat(login("nobody-" + email, "correct-horse-battery").getStatusCode().value())
                .isEqualTo(401);
    }

    // ── dev headers are gone; only Authorization: Bearer works ──────────────

    @Test
    void devHeadersNoLongerWork_onlyBearerTokenAuthenticates() {
        String slug = "nodev-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> signedUp = signup(slug, slug + "@test.local", "correct-horse-battery");
        String companyId = parse(signedUp.getBody()).get("companyId").asText();
        String token = parse(signedUp.getBody()).get("token").asText();

        // the old X-Company-Id header alone does nothing now
        HttpHeaders devHeader = jsonHeaders();
        devHeader.set("X-Company-Id", companyId);
        ResponseEntity<String> rejected = rest.exchange(
                "/api/v1/companies/" + companyId + "/roster", HttpMethod.GET,
                new HttpEntity<>(devHeader), String.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(401);

        // the real bearer token works
        ResponseEntity<String> accepted = rest.exchange(
                "/api/v1/companies/" + companyId + "/roster", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);
        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
    }

    // ── tenant isolation across two signed-up companies ─────────────────────

    @Test
    void secondCompanysTokenCannotSeeTheFirst() {
        String slugA = "iso-a-" + UUID.randomUUID().toString().substring(0, 8);
        String slugB = "iso-b-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode a = parse(signup(slugA, slugA + "@test.local", "correct-horse-battery").getBody());
        JsonNode b = parse(signup(slugB, slugB + "@test.local", "correct-horse-battery").getBody());

        ResponseEntity<String> crossRoster = rest.exchange(
                "/api/v1/companies/" + a.get("companyId").asText() + "/roster", HttpMethod.GET,
                new HttpEntity<>(bearer(b.get("token").asText())), String.class);
        assertThat(crossRoster.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> crossCompany = rest.exchange(
                "/api/v1/companies/" + a.get("companyId").asText(), HttpMethod.GET,
                new HttpEntity<>(bearer(b.get("token").asText())), String.class);
        assertThat(crossCompany.getStatusCode().value()).isEqualTo(404);
    }
}
