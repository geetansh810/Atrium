package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 16 §3 Done-when: seed → browse shows it with provenance; forget archives
 * (never hard-deletes); tenant isolation across companies. No real embeddings
 * provider needed — {@link EmbeddingClient} is mocked here, same precedent as
 * {@link SkillContextAssemblerTest}.
 */
class MemoryApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @BeforeEach
    void stubEmbeddings() {
        when(embeddingClient.isReady()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(new float[1536]);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private HttpHeaders headers(String companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) headers.set("X-Company-Id", companyId);
        return headers;
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Unparseable response: " + body, e);
        }
    }

    private String createCompany(String slugPrefix) {
        String slug = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/companies",
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), headers(null)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private ResponseEntity<String> seed(String companyId, Map<String, Object> body) {
        return rest.postForEntity("/api/v1/companies/" + companyId + "/memories",
                new HttpEntity<>(body, headers(companyId)), String.class);
    }

    private JsonNode browse(String companyId, String queryString) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/companies/" + companyId + "/memories" + (queryString == null ? "" : queryString),
                HttpMethod.GET, new HttpEntity<>(headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return parse(response.getBody()).get("data");
    }

    // ── seed -> browse shows it with provenance ─────────────────────────────

    @Test
    void seededMemoryAppearsInBrowseWithProvenance() {
        String company = createCompany("mem-seed");

        ResponseEntity<String> created = seed(company, Map.of(
                "scope", "company", "kind", "preference", "content", "CEO prefers bullet lists"));
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        JsonNode body = parse(created.getBody());
        assertThat(body.get("content").asText()).isEqualTo("CEO prefers bullet lists");
        assertThat(body.get("status").asText()).isEqualTo("active");
        assertThat(body.get("provenance").get("extractedBy").asText()).isEqualTo("user");

        JsonNode listed = browse(company, null);
        assertThat(listed.findValuesAsText("content")).contains("CEO prefers bullet lists");
    }

    // ── forget archives, never hard-deletes ─────────────────────────────────

    @Test
    void forgetArchivesRatherThanDeleting() {
        String company = createCompany("mem-forget");
        ResponseEntity<String> created = seed(company, Map.of(
                "scope", "company", "kind", "fact", "content", "to be forgotten"));
        String memoryId = parse(created.getBody()).get("id").asText();

        ResponseEntity<String> forget = rest.exchange("/api/v1/memories/" + memoryId,
                HttpMethod.DELETE, new HttpEntity<>(headers(company)), String.class);
        assertThat(forget.getStatusCode().value()).isEqualTo(204);

        // still present, now archived — never hard-deleted
        JsonNode archived = browse(company, "?status=archived");
        assertThat(archived.findValuesAsText("id")).contains(memoryId);
        JsonNode activeOnly = browse(company, "?status=active");
        assertThat(activeOnly.findValuesAsText("id")).doesNotContain(memoryId);
    }

    // ── tenant isolation across companies ────────────────────────────────────

    @Test
    void memoriesAreIsolatedAcrossCompanies() {
        String companyA = createCompany("mem-iso-a");
        String companyB = createCompany("mem-iso-b");

        seed(companyA, Map.of("scope", "company", "kind", "fact", "content", "Company A's secret"));

        JsonNode listedB = browse(companyB, null);
        assertThat(listedB.findValuesAsText("content")).doesNotContain("Company A's secret");
    }

    // ── validation: agent scope requires a real agentId in this company ────

    @Test
    void agentScopeRequiresAKnownAgentIdInTheSameCompany() {
        String company = createCompany("mem-validate");

        ResponseEntity<String> missingAgentId = seed(company, Map.of(
                "scope", "agent", "kind", "fact", "content", "no agent given"));
        assertThat(missingAgentId.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<String> unknownAgent = seed(company, Map.of(
                "scope", "agent", "agentId", UUID.randomUUID().toString(), "kind", "fact",
                "content", "unknown agent"));
        assertThat(unknownAgent.getStatusCode().value()).isEqualTo(404);
    }
}
