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

/** M0.2 Done-when: 07's check + bad runtimeConfig → 400 field errors + seeded /model-catalog. */
class RegistryApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    // ── helpers ────────────────────────────────────────────────────────────

    private HttpHeaders tenantHeaders(String companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) {
            headers.set("X-Company-Id", companyId);
        }
        return headers;
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Unparseable response: " + body, e);
        }
    }

    private String createCompany(String slug) {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/companies",
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), tenantHeaders(null)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private ResponseEntity<String> hire(String companyId, Map<String, Object> body) {
        return rest.postForEntity("/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(body, tenantHeaders(companyId)), String.class);
    }

    private Map<String, Object> hireBody(String name, String templateKey, String skill) {
        return Map.of(
                "name", name,
                "roleTemplateKey", templateKey,
                "roleTitle", "Title of " + name,
                "skillTags", java.util.List.of(skill),
                "modelProvider", "anthropic",
                "modelName", "claude-sonnet-5");
    }

    // ── 07 M0.2 check: company + 3 agents via API; roster; template resolution ──

    @Test
    void companyAndThreeAgentsViaApi_rosterReturnsThem_templateResolves() {
        String company = createCompany("acme-" + UUID.randomUUID().toString().substring(0, 8));

        ResponseEntity<String> coder = hire(company, hireBody("CoderAgent", "coder", "coding"));
        assertThat(coder.getStatusCode().value()).as(coder.getBody()).isEqualTo(201);
        assertThat(parse(coder.getBody()).get("roleDefinitionId").asText()).isNotBlank();
        assertThat(parse(coder.getBody()).get("runtimeType").asText()).isEqualTo("llm_loop");

        ResponseEntity<String> tester = hire(company, hireBody("TesterAgent", "tester", "testing"));
        assertThat(tester.getStatusCode().value()).isEqualTo(201);

        // third agent hired via explicit roleDefinitionId — resolved from /role-definitions
        ResponseEntity<String> definitions = rest.exchange("/api/v1/role-definitions", HttpMethod.GET,
                new HttpEntity<>(tenantHeaders(company)), String.class);
        assertThat(definitions.getStatusCode().value()).isEqualTo(200);
        String researchDefinitionId = null;
        for (JsonNode rd : parse(definitions.getBody())) {
            if (rd.get("key").asText().equals("research")) {
                researchDefinitionId = rd.get("id").asText();
            }
        }
        assertThat(researchDefinitionId).as("seeded research template visible").isNotNull();

        ResponseEntity<String> researcher = hire(company, Map.of(
                "name", "ResearchAgent",
                "roleDefinitionId", researchDefinitionId,
                "roleTitle", "Research Specialist",
                "skillTags", java.util.List.of("research"),
                "modelProvider", "anthropic",
                "modelName", "claude-haiku-4-5"));
        assertThat(researcher.getStatusCode().value()).as(researcher.getBody()).isEqualTo(201);

        ResponseEntity<String> roster = rest.exchange("/api/v1/companies/" + company + "/roster",
                HttpMethod.GET, new HttpEntity<>(tenantHeaders(company)), String.class);
        assertThat(roster.getStatusCode().value()).isEqualTo(200);
        JsonNode agents = parse(roster.getBody());
        assertThat(agents).hasSize(3);
        assertThat(agents.findValuesAsText("name"))
                .containsExactlyInAnyOrder("CoderAgent", "TesterAgent", "ResearchAgent");
    }

    // ── Rev C check: bad runtimeConfig → 400 with field errors ──────────────

    @Test
    void hiringWithBadRuntimeConfigReturns400FieldErrors() {
        String company = createCompany("badcfg-" + UUID.randomUUID().toString().substring(0, 8));

        ResponseEntity<String> response = hire(company, Map.of(
                "name", "BrokenAgent",
                "roleTemplateKey", "coder",
                "roleTitle", "Engineer",
                "skillTags", java.util.List.of("coding"),
                "modelProvider", "anthropic",
                "modelName", "claude-sonnet-5",
                "runtimeConfig", Map.of("pollSeconds", -5, "bogusKey", 1)));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode fieldErrors = parse(response.getBody()).get("fieldErrors");
        assertThat(fieldErrors).isNotNull();
        assertThat(fieldErrors.has("runtimeConfig.pollSeconds")).isTrue();
        assertThat(fieldErrors.has("runtimeConfig.bogusKey")).isTrue();

        ResponseEntity<String> unknownType = hire(company, Map.of(
                "name", "AlienAgent",
                "roleTemplateKey", "coder",
                "roleTitle", "Engineer",
                "skillTags", java.util.List.of("coding"),
                "modelProvider", "anthropic",
                "modelName", "claude-sonnet-5",
                "runtimeType", "quantum_loop"));
        assertThat(unknownType.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(unknownType.getBody()).get("fieldErrors").has("runtimeType")).isTrue();
    }

    // ── Rev C check: /model-catalog returns seeded rows, prices omitted ─────

    @Test
    void modelCatalogReturnsSeededRowsWithoutPrices() {
        String company = createCompany("catalog-" + UUID.randomUUID().toString().substring(0, 8));

        ResponseEntity<String> response = rest.exchange("/api/v1/model-catalog", HttpMethod.GET,
                new HttpEntity<>(tenantHeaders(company)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        JsonNode catalog = parse(response.getBody());
        assertThat(catalog.findValuesAsText("modelName"))
                .contains("claude-fable-5", "claude-sonnet-5", "claude-haiku-4-5");
        assertThat(response.getBody()).doesNotContain("price", "Price");
    }

    // ── Cross-cutting DoD: tenant isolation ─────────────────────────────────

    @Test
    void secondCompanySeesNothingOfTheFirst() {
        String companyA = createCompany("iso-a-" + UUID.randomUUID().toString().substring(0, 8));
        String companyB = createCompany("iso-b-" + UUID.randomUUID().toString().substring(0, 8));
        hire(companyA, hireBody("SecretAgent", "coder", "coding"));

        // B's roster is empty
        ResponseEntity<String> rosterB = rest.exchange("/api/v1/companies/" + companyB + "/roster",
                HttpMethod.GET, new HttpEntity<>(tenantHeaders(companyB)), String.class);
        assertThat(parse(rosterB.getBody())).isEmpty();

        // B cannot read A's company or roster — 404, indistinguishable from absence
        ResponseEntity<String> crossCompany = rest.exchange("/api/v1/companies/" + companyA,
                HttpMethod.GET, new HttpEntity<>(tenantHeaders(companyB)), String.class);
        assertThat(crossCompany.getStatusCode().value()).isEqualTo(404);
        ResponseEntity<String> crossRoster = rest.exchange("/api/v1/companies/" + companyA + "/roster",
                HttpMethod.GET, new HttpEntity<>(tenantHeaders(companyB)), String.class);
        assertThat(crossRoster.getStatusCode().value()).isEqualTo(404);

        // B cannot patch A's agent
        String agentA = parse(hire(companyA, hireBody("Agent2", "tester", "testing")).getBody())
                .get("id").asText();
        ResponseEntity<String> crossPatch = rest.exchange("/api/v1/agents/" + agentA,
                HttpMethod.PATCH, new HttpEntity<>(Map.of("name", "Hijacked"), tenantHeaders(companyB)),
                String.class);
        assertThat(crossPatch.getStatusCode().value()).isEqualTo(404);
    }

    // ── 05 §registry: manager-cycle validation ──────────────────────────────

    @Test
    void managerCycleIsRejected() {
        String company = createCompany("cycle-" + UUID.randomUUID().toString().substring(0, 8));
        String alpha = parse(hire(company, hireBody("Alpha", "coder", "coding")).getBody())
                .get("id").asText();
        String beta = parse(hire(company, hireBody("Beta", "coder", "coding")).getBody())
                .get("id").asText();

        // Beta reports to Alpha — fine
        ResponseEntity<String> ok = rest.exchange("/api/v1/agents/" + beta, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("managerAgentId", alpha), tenantHeaders(company)), String.class);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);

        // Alpha reporting to Beta closes the loop — rejected
        ResponseEntity<String> cycle = rest.exchange("/api/v1/agents/" + alpha, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("managerAgentId", beta), tenantHeaders(company)), String.class);
        assertThat(cycle.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(cycle.getBody()).get("fieldErrors").has("managerAgentId")).isTrue();

        // Self-management rejected too
        ResponseEntity<String> self = rest.exchange("/api/v1/agents/" + alpha, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("managerAgentId", alpha), tenantHeaders(company)), String.class);
        assertThat(self.getStatusCode().value()).isEqualTo(400);
    }

    // ── Dev-auth guard: /api without tenant header is rejected ─────────────

    @Test
    void apiWithoutTenantHeaderIsRejected() {
        ResponseEntity<String> response = rest.exchange("/api/v1/role-definitions", HttpMethod.GET,
                new HttpEntity<>(tenantHeaders(null)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("X-Company-Id");
    }
}
