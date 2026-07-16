package app.atrium.communication;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** M2.5 API surface (04 §Communication): channels, messages, announcements. */
class CommunicationApiTest extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private final Map<String, String> tokenByCompany = new HashMap<>();
    private final Map<String, String> adminUserIdByCompany = new HashMap<>();

    private HttpHeaders headers(String companyId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) h.setBearerAuth(tokenByCompany.get(companyId));
        return h;
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Unparseable response: " + body, e);
        }
    }

    /** M3.1: every company needs a signed-up admin now — this issues the JWT the rest of the file's calls carry. */
    private String createCompany(String slugPrefix) {
        String slug = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        HttpHeaders signupHeaders = new HttpHeaders();
        signupHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.postForEntity("/api/v1/auth/signup",
                new HttpEntity<>(Map.of(
                        "companyName", "Co " + slug, "companySlug", slug,
                        "displayName", "Admin", "email", slug + "@test.local", "password", "testpass123"),
                        signupHeaders),
                String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(201);
        JsonNode body = parse(r.getBody());
        String companyId = body.get("companyId").asText();
        tokenByCompany.put(companyId, body.get("token").asText());
        adminUserIdByCompany.put(companyId, body.get("userId").asText());
        return companyId;
    }

    private ResponseEntity<String> post(String path, Object body, String companyId) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(companyId)), String.class);
    }

    private ResponseEntity<String> get(String path, String companyId) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(companyId)), String.class);
    }

    private long outboxCount(String companyId, String eventType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE company_id = ?::uuid AND event_type = ?",
                Long.class, companyId, eventType);
    }

    @Test
    void channelsCreateListAndGuardDuplicatesAndKind() {
        String company = createCompany("m25-ch");

        ResponseEntity<String> created = post("/api/v1/companies/" + company + "/channels",
                Map.of("name", "dev-team"), company);
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        assertThat(parse(created.getBody()).get("kind").asText()).isEqualTo("channel");

        // list shows it
        ResponseEntity<String> list = get("/api/v1/companies/" + company + "/channels", company);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(parse(list.getBody())).anySatisfy(c -> assertThat(c.get("name").asText()).isEqualTo("dev-team"));

        // duplicate name → 409
        assertThat(post("/api/v1/companies/" + company + "/channels", Map.of("name", "dev-team"), company)
                .getStatusCode().value()).isEqualTo(409);

        // invalid kind → 400
        assertThat(post("/api/v1/companies/" + company + "/channels",
                Map.of("name", "weird", "kind", "megaphone"), company).getStatusCode().value()).isEqualTo(400);

        // blank name → 400 (@NotBlank)
        assertThat(post("/api/v1/companies/" + company + "/channels", Map.of("name", ""), company)
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void sendMessagePersistsEmitsOutboxAndPaginates() {
        String company = createCompany("m25-msg");
        String channelId = parse(post("/api/v1/companies/" + company + "/channels",
                Map.of("name", "general"), company).getBody()).get("id").asText();
        long outboxBefore = outboxCount(company, "chat.message");

        for (String text : List.of("first", "second", "third")) {
            ResponseEntity<String> sent = post("/api/v1/channels/" + channelId + "/messages",
                    Map.of("text", text), company);
            assertThat(sent.getStatusCode().value()).as(sent.getBody()).isEqualTo(201);
            // M3.1: every authenticated call now carries a real user (the signed-up admin)
            assertThat(parse(sent.getBody()).get("sender").asText())
                    .isEqualTo("user:" + adminUserIdByCompany.get(company));
        }
        assertThat(outboxCount(company, "chat.message")).isEqualTo(outboxBefore + 3);

        // page 1: newest 2 + a cursor
        JsonNode page1 = parse(get("/api/v1/channels/" + channelId + "/messages?limit=2", company).getBody());
        assertThat(page1.get("data")).hasSize(2);
        assertThat(page1.get("nextCursor").isNull()).isFalse();

        // page 2: the remaining 1, no cursor
        String cursor = page1.get("nextCursor").asText();
        JsonNode page2 = parse(get("/api/v1/channels/" + channelId + "/messages?limit=2&before=" + cursor, company).getBody());
        assertThat(page2.get("data")).hasSize(1);

        // union of both pages = all 3 distinct messages (robust to same-ms created_at ties)
        Set<String> ids = new HashSet<>();
        page1.get("data").forEach(m -> ids.add(m.get("id").asText()));
        page2.get("data").forEach(m -> ids.add(m.get("id").asText()));
        assertThat(ids).hasSize(3);
    }

    @Test
    void announcementsCreateListEmitOutboxAndGuardCategory() {
        String company = createCompany("m25-ann");
        long before = outboxCount(company, "announcement.created");

        ResponseEntity<String> created = post("/api/v1/companies/" + company + "/announcements",
                Map.of("title", "All hands", "body", "11am tomorrow", "category", "company"), company);
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        assertThat(outboxCount(company, "announcement.created")).isEqualTo(before + 1);

        JsonNode list = parse(get("/api/v1/companies/" + company + "/announcements", company).getBody());
        assertThat(list).anySatisfy(a -> assertThat(a.get("title").asText()).isEqualTo("All hands"));

        // invalid category → 400
        assertThat(post("/api/v1/companies/" + company + "/announcements",
                Map.of("title", "x", "category", "spam"), company).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void tenantIsolationAcrossCompanies() {
        String companyA = createCompany("m25-a");
        String companyB = createCompany("m25-b");
        String channelA = parse(post("/api/v1/companies/" + companyA + "/channels",
                Map.of("name", "general"), companyA).getBody()).get("id").asText();
        post("/api/v1/channels/" + channelA + "/messages", Map.of("text", "secret"), companyA);

        // B's own channel list is empty
        assertThat(parse(get("/api/v1/companies/" + companyB + "/channels", companyB).getBody())).isEmpty();

        // B cannot read A's channel messages → 404
        assertThat(get("/api/v1/channels/" + channelA + "/messages", companyB).getStatusCode().value()).isEqualTo(404);

        // B cannot post into A's channel → 404
        assertThat(post("/api/v1/channels/" + channelA + "/messages", Map.of("text", "intrude"), companyB)
                .getStatusCode().value()).isEqualTo(404);

        // posting to a non-existent channel → 404
        assertThat(post("/api/v1/channels/" + UUID.randomUUID() + "/messages", Map.of("text", "ghost"), companyA)
                .getStatusCode().value()).isEqualTo(404);
    }
}
