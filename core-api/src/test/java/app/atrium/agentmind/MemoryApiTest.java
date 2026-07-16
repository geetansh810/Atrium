package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MemoryStore memoryStore;

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

    // ── M-LN2-fix: seeding must not require an embeddings key ───────────────

    /**
     * {@code MemoryService.seed} is the one caller of {@code MemoryStore#ingest} that was
     * never behind any readiness gate — a human seeding a company preference via this
     * endpoint used to get an unhandled {@code IllegalStateException} the moment
     * {@code OPENAI_API_KEY} wasn't configured. Fixed alongside the learning pipeline's
     * fix: the row now lands with a NULL embedding instead.
     */
    @Test
    void seedingSucceedsWithoutAnEmbeddingsProviderConfigured() {
        when(embeddingClient.isReady()).thenReturn(false);
        String company = createCompany("mem-seed-noembed");

        ResponseEntity<String> created = seed(company, Map.of(
                "scope", "company", "kind", "preference", "content", "seeded without embeddings"));
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        String memoryId = parse(created.getBody()).get("id").asText();

        String embedding = jdbc.queryForObject(
                "SELECT embedding::text FROM memories WHERE id = ?::uuid", String.class, memoryId);
        assertThat(embedding).isNull();

        // still browsable (plain keyset listing never depended on embeddings)
        JsonNode listed = browse(company, null);
        assertThat(listed.findValuesAsText("content")).contains("seeded without embeddings");
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

    // ── M-LN1: review-queue + review action ──────────────────────────────────

    private UUID seedPendingReview(String companyId, String scope, String kind, String content) {
        ObjectNode provenance = json.createObjectNode();
        provenance.put("extractedBy", "pipeline");
        return memoryStore.ingest(new MemoryWrite(UUID.fromString(companyId), scope, null, null, null,
                kind, content, (short) 1, "pending_review", provenance, null));
    }

    @Test
    void reviewQueueShowsPendingItemsAndApproveActivatesAndRemovesThem() {
        String company = createCompany("mem-review");
        UUID memoryId = seedPendingReview(company, "company", "fact", "our brand name is X-Corp");

        JsonNode queue = parse(rest.exchange("/api/v1/companies/" + company + "/memories/review-queue",
                HttpMethod.GET, new HttpEntity<>(headers(company)), String.class).getBody()).get("data");
        assertThat(queue.findValuesAsText("id")).contains(memoryId.toString());

        ResponseEntity<String> reviewed = rest.postForEntity("/api/v1/memories/" + memoryId + "/review",
                new HttpEntity<>(Map.of("action", "approve"), headers(company)), String.class);
        assertThat(reviewed.getStatusCode().value()).as(reviewed.getBody()).isEqualTo(200);
        JsonNode body = parse(reviewed.getBody());
        assertThat(body.get("status").asText()).isEqualTo("active");
        assertThat(body.get("provenance").get("reviewedBy").asText()).isNotBlank();

        JsonNode queueAfter = parse(rest.exchange("/api/v1/companies/" + company + "/memories/review-queue",
                HttpMethod.GET, new HttpEntity<>(headers(company)), String.class).getBody()).get("data");
        assertThat(queueAfter.findValuesAsText("id")).doesNotContain(memoryId.toString());
    }

    @Test
    void rejectedMemoryNeverAppearsInRecall() {
        // A non-zero vector everywhere — a zero vector's cosine similarity is
        // undefined (same precedent as LlmLoopRuntimeTest), and the column is
        // vector(1536) so the stub must match that dimension exactly.
        float[] sameVectorEverywhere = new float[1536];
        sameVectorEverywhere[0] = 1f;
        when(embeddingClient.embed(anyString())).thenReturn(sameVectorEverywhere);

        String company = createCompany("mem-reject");
        UUID memoryId = seedPendingReview(company, "company", "preference", "never rejected content");
        UUID companyId = UUID.fromString(company);

        // pending_review is already excluded from recall by construction (15 §5 invariant:
        // only status='active' is ever recalled) — the real thing worth guarding here is
        // that rejecting it doesn't accidentally leave it recallable.
        List<MemoryHit> beforeReject = memoryStore.recall(
                new RecallQuery(companyId, UUID.randomUUID(), null, "never rejected content", 10, null));
        assertThat(beforeReject).extracting(h -> h.memory().id()).doesNotContain(memoryId);

        ResponseEntity<String> reviewed = rest.postForEntity("/api/v1/memories/" + memoryId + "/review",
                new HttpEntity<>(Map.of("action", "reject"), headers(company)), String.class);
        assertThat(reviewed.getStatusCode().value()).as(reviewed.getBody()).isEqualTo(200);
        assertThat(parse(reviewed.getBody()).get("status").asText()).isEqualTo("rejected");

        List<MemoryHit> afterReject = memoryStore.recall(
                new RecallQuery(companyId, UUID.randomUUID(), null, "never rejected content", 10, null));
        assertThat(afterReject).extracting(h -> h.memory().id()).doesNotContain(memoryId);
    }

    @Test
    void approvingWithPromoteScopeAndAsSkillAppliesBoth() {
        String company = createCompany("mem-promote");
        UUID memoryId = seedPendingReview(company, "company", "lesson", "always write tests first");

        ResponseEntity<String> reviewed = rest.postForEntity("/api/v1/memories/" + memoryId + "/review",
                new HttpEntity<>(Map.of("action", "approve", "promoteScope", "company",
                        "asSkill", Map.of("key", "tdd-first", "name", "Write tests first")),
                        headers(company)), String.class);
        assertThat(reviewed.getStatusCode().value()).as(reviewed.getBody()).isEqualTo(200);
        assertThat(parse(reviewed.getBody()).get("status").asText()).isEqualTo("active");

        Integer draftSkillCount = jdbc.queryForObject(
                "SELECT count(*) FROM skills WHERE company_id = ?::uuid AND key = 'tdd-first' "
                        + "AND trust_level = 'agent_proposed'", Integer.class, company);
        assertThat(draftSkillCount).isEqualTo(1);
    }
}
