package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
 * M-KN1 Done-when: ingest -> list shows it active; archive never hard-deletes;
 * tenant isolation; role attach restricted to company-owned role definitions
 * (same rule as {@code SkillsApiTest}'s equivalent case). The prompt-fixture
 * half of the Done-when (role attach respected in an actual assembled prompt)
 * lives in {@code app.atrium.execution.LlmLoopRuntimeTest}.
 */
class KnowledgeApiTest extends IntegrationTestBase {

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
        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<?> texts = inv.getArgument(0);
            return texts.stream().map(t -> new float[1536]).toList();
        });
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

    private ResponseEntity<String> ingest(String companyId, Map<String, Object> body) {
        return rest.postForEntity("/api/v1/companies/" + companyId + "/knowledge",
                new HttpEntity<>(body, headers(companyId)), String.class);
    }

    private JsonNode list(String companyId, String queryString) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/companies/" + companyId + "/knowledge" + (queryString == null ? "" : queryString),
                HttpMethod.GET, new HttpEntity<>(headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return parse(response.getBody());
    }

    // ── ingest -> list shows the doc active ─────────────────────────────────

    @Test
    void ingestedDocAppearsInListActive() {
        String company = createCompany("kn-ingest");

        ResponseEntity<String> created = ingest(company, Map.of(
                "title", "Brand guide", "content", "Our brand voice is friendly and concise."));
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        JsonNode body = parse(created.getBody());
        assertThat(body.get("title").asText()).isEqualTo("Brand guide");
        assertThat(body.get("status").asText()).isEqualTo("active");
        assertThat(body.get("mime").asText()).isEqualTo("text/markdown");

        JsonNode listed = list(company, null);
        assertThat(listed.findValuesAsText("title")).contains("Brand guide");
    }

    // ── archive never hard-deletes ───────────────────────────────────────────

    @Test
    void archiveNeverHardDeletes() {
        String company = createCompany("kn-archive");
        ResponseEntity<String> created = ingest(company, Map.of(
                "title", "Old policy", "content", "This policy is being retired."));
        String docId = parse(created.getBody()).get("id").asText();

        ResponseEntity<String> archived = rest.exchange("/api/v1/knowledge/" + docId,
                HttpMethod.DELETE, new HttpEntity<>(headers(company)), String.class);
        assertThat(archived.getStatusCode().value()).isEqualTo(204);

        JsonNode archivedOnly = list(company, "?status=archived");
        assertThat(archivedOnly.findValuesAsText("id")).contains(docId);
        JsonNode activeOnly = list(company, "?status=active");
        assertThat(activeOnly.findValuesAsText("id")).doesNotContain(docId);
    }

    // ── tenant isolation ─────────────────────────────────────────────────────

    @Test
    void knowledgeDocsAreIsolatedAcrossCompanies() {
        String companyA = createCompany("kn-iso-a");
        String companyB = createCompany("kn-iso-b");

        ingest(companyA, Map.of("title", "Company A secret doc", "content", "Confidential to A."));

        JsonNode listedB = list(companyB, null);
        assertThat(listedB.findValuesAsText("title")).doesNotContain("Company A secret doc");
    }

    // ── role attach restricted to company-owned role definitions ────────────

    @Test
    void attachToRoleOnlyAffectsCompanyOwnedRoleDefinition() {
        String company = createCompany("kn-role");

        ResponseEntity<String> customRole = rest.postForEntity("/api/v1/role-definitions",
                new HttpEntity<>(Map.of(
                        "key", "content-writer",
                        "title", "Content Writer",
                        "systemPrompt", "You write on-brand content.",
                        "outputContract", "markdown"),
                        headers(company)), String.class);
        assertThat(customRole.getStatusCode().value()).isEqualTo(201);
        String roleId = parse(customRole.getBody()).get("id").asText();

        ResponseEntity<String> created = ingest(company, Map.of(
                "title", "Brand guide", "content", "Our brand voice is friendly and concise."));
        String docId = parse(created.getBody()).get("id").asText();

        ResponseEntity<String> attach = rest.postForEntity(
                "/api/v1/role-definitions/" + roleId + "/knowledge",
                new HttpEntity<>(Map.of("docId", docId), headers(company)), String.class);
        assertThat(attach.getStatusCode().value()).isEqualTo(204);

        // attaching to a GLOBAL template id is rejected — would leak cross-tenant
        ResponseEntity<String> globalTemplate = rest.exchange("/api/v1/role-definitions",
                HttpMethod.GET, new HttpEntity<>(headers(company)), String.class);
        String coderTemplateId = null;
        for (JsonNode rd : parse(globalTemplate.getBody())) {
            if ("coder".equals(rd.get("key").asText())) {
                coderTemplateId = rd.get("id").asText();
            }
        }
        assertThat(coderTemplateId).isNotNull();
        ResponseEntity<String> rejectedAttach = rest.postForEntity(
                "/api/v1/role-definitions/" + coderTemplateId + "/knowledge",
                new HttpEntity<>(Map.of("docId", docId), headers(company)), String.class);
        assertThat(rejectedAttach.getStatusCode().value()).isEqualTo(404);
    }
}
