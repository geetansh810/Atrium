package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * M-SK1 Done-when: hire from template → agent has template skills; attach/detach
 * reflected in /mind; global+company listing correct across two seeded companies
 * (isolation).
 */
class SkillsApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    // ── helpers (mirrors RegistryApiTest's conventions) ─────────────────────

    private final Map<String, String> tokenByCompany = new HashMap<>();

    private HttpHeaders tenantHeaders(String companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) {
            headers.setBearerAuth(tokenByCompany.get(companyId));
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

    /** M3.1: every company needs a signed-up admin now — this issues the JWT the rest of the file's calls carry. */
    private String createCompany(String slug) {
        HttpHeaders signupHeaders = new HttpHeaders();
        signupHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/signup",
                new HttpEntity<>(Map.of(
                        "companyName", "Co " + slug, "companySlug", slug,
                        "displayName", "Admin", "email", slug + "@test.local", "password", "testpass123"),
                        signupHeaders),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        JsonNode body = parse(response.getBody());
        String companyId = body.get("companyId").asText();
        tokenByCompany.put(companyId, body.get("token").asText());
        return companyId;
    }

    private String hireAgent(String companyId, String templateKey, String name) {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", name,
                        "roleTemplateKey", templateKey,
                        "roleTitle", "Title of " + name,
                        "skillTags", List.of(templateKey),
                        "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"),
                        tenantHeaders(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private JsonNode mind(String companyId, String agentId) {
        ResponseEntity<String> response = rest.exchange("/api/v1/agents/" + agentId + "/mind",
                HttpMethod.GET, new HttpEntity<>(tenantHeaders(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return parse(response.getBody());
    }

    private JsonNode listSkills(String companyId, String queryString) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/companies/" + companyId + "/skills" + (queryString == null ? "" : queryString),
                HttpMethod.GET, new HttpEntity<>(tenantHeaders(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return parse(response.getBody());
    }

    // ── hire from template auto-attaches the template's skill set ──────────

    @Test
    void hireFromTemplateAutoAttachesTemplateSkills() {
        String company = createCompany("hire-" + UUID.randomUUID().toString().substring(0, 8));
        String coder = hireAgent(company, "coder", "CoderAgent");

        JsonNode mind = mind(company, coder);
        List<String> skillKeys = mind.get("skills").findValuesAsText("key");
        assertThat(skillKeys).containsExactlyInAnyOrder("code-review-checklist", "unified-diff-output");
        for (JsonNode skill : mind.get("skills")) {
            assertThat(skill.get("source").asText()).isEqualTo("hired");
        }

        String researcher = hireAgent(company, "research", "ResearchAgent");
        List<String> researchSkillKeys = mind(company, researcher).get("skills").findValuesAsText("key");
        assertThat(researchSkillKeys)
                .containsExactlyInAnyOrder("source-ranking-procedure", "summarization-contract");
    }

    // ── attach/detach reflected in /mind ────────────────────────────────────

    @Test
    void attachAndDetachAreReflectedInMind() {
        String company = createCompany("attach-" + UUID.randomUUID().toString().substring(0, 8));
        String agent = hireAgent(company, "tester", "TesterAgent");

        // custom company skill
        ResponseEntity<String> created = rest.postForEntity("/api/v1/companies/" + company + "/skills",
                new HttpEntity<>(Map.of(
                        "key", "custom-lint-rules",
                        "name", "Custom lint rules",
                        "description", "House style",
                        "bodyMd", "# Custom lint rules\nNo tabs, 2-space indent.",
                        "kind", "reference",
                        "tags", List.of("style")),
                        tenantHeaders(company)), String.class);
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        String skillId = parse(created.getBody()).get("id").asText();

        ResponseEntity<String> attach = rest.postForEntity("/api/v1/agents/" + agent + "/skills",
                new HttpEntity<>(Map.of("skillId", skillId, "proficiency", 5), tenantHeaders(company)),
                String.class);
        assertThat(attach.getStatusCode().value()).isEqualTo(204);

        JsonNode afterAttach = mind(company, agent);
        List<String> keysAfterAttach = afterAttach.get("skills").findValuesAsText("key");
        assertThat(keysAfterAttach).contains("custom-lint-rules", "test-coverage-checklist", "bug-report-contract");
        JsonNode attached = null;
        for (JsonNode skill : afterAttach.get("skills")) {
            if (skill.get("key").asText().equals("custom-lint-rules")) attached = skill;
        }
        assertThat(attached).isNotNull();
        assertThat(attached.get("source").asText()).isEqualTo("assigned");
        assertThat(attached.get("proficiency").asInt()).isEqualTo(5);

        ResponseEntity<String> detach = rest.exchange("/api/v1/agents/" + agent + "/skills/" + skillId,
                HttpMethod.DELETE, new HttpEntity<>(tenantHeaders(company)), String.class);
        assertThat(detach.getStatusCode().value()).isEqualTo(204);

        JsonNode afterDetach = mind(company, agent);
        assertThat(afterDetach.get("skills").findValuesAsText("key")).doesNotContain("custom-lint-rules");
        // hired skills untouched by an unrelated detach
        assertThat(afterDetach.get("skills").findValuesAsText("key"))
                .contains("test-coverage-checklist", "bug-report-contract");
    }

    // ── global + company listing correct across two companies (isolation) ──

    @Test
    void globalAndCompanyListingIsolatedAcrossCompanies() {
        String companyA = createCompany("iso-a-" + UUID.randomUUID().toString().substring(0, 8));
        String companyB = createCompany("iso-b-" + UUID.randomUUID().toString().substring(0, 8));

        ResponseEntity<String> created = rest.postForEntity("/api/v1/companies/" + companyA + "/skills",
                new HttpEntity<>(Map.of(
                        "key", "secret-playbook",
                        "name", "Secret playbook",
                        "description", "Company A only",
                        "bodyMd", "# Secret",
                        "kind", "procedure",
                        "tags", List.of()),
                        tenantHeaders(companyA)), String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);

        JsonNode skillsA = listSkills(companyA, null);
        List<String> keysA = skillsA.findValuesAsText("key");
        assertThat(keysA).contains("secret-playbook", "code-review-checklist", "source-ranking-procedure");

        JsonNode skillsB = listSkills(companyB, null);
        List<String> keysB = skillsB.findValuesAsText("key");
        assertThat(keysB).doesNotContain("secret-playbook");
        // B still sees every global platform skill
        assertThat(keysB).contains("code-review-checklist", "unified-diff-output",
                "test-coverage-checklist", "bug-report-contract", "source-ranking-procedure",
                "summarization-contract");

        // kind filter
        JsonNode policiesOnlyA = listSkills(companyA, "?kind=policy");
        for (JsonNode s : policiesOnlyA) {
            assertThat(s.get("kind").asText()).isEqualTo("policy");
        }
        assertThat(policiesOnlyA.findValuesAsText("key")).contains("bug-report-contract", "summarization-contract");

        // versioning: latest version wins, no duplicate rows in the listing
        String skillId = parse(created.getBody()).get("id").asText();
        ResponseEntity<String> versioned = rest.postForEntity("/api/v1/skills/" + skillId + "/versions",
                new HttpEntity<>(Map.of(
                        "name", "Secret playbook v2",
                        "description", "Company A only, revised",
                        "bodyMd", "# Secret v2",
                        "kind", "procedure",
                        "tags", List.of()),
                        tenantHeaders(companyA)), String.class);
        assertThat(versioned.getStatusCode().value()).as(versioned.getBody()).isEqualTo(201);
        assertThat(parse(versioned.getBody()).get("version").asInt()).isEqualTo(2);

        JsonNode afterVersion = listSkills(companyA, null);
        long secretRows = 0;
        for (JsonNode s : afterVersion) {
            if (s.get("key").asText().equals("secret-playbook")) secretRows++;
        }
        assertThat(secretRows).isEqualTo(1);
        for (JsonNode s : afterVersion) {
            if (s.get("key").asText().equals("secret-playbook")) {
                assertThat(s.get("version").asInt()).isEqualTo(2);
                assertThat(s.get("name").asText()).isEqualTo("Secret playbook v2");
            }
        }

        // company B cannot version company A's skill
        ResponseEntity<String> crossVersion = rest.postForEntity("/api/v1/skills/" + skillId + "/versions",
                new HttpEntity<>(Map.of(
                        "name", "Hijacked", "description", "x", "bodyMd", "x", "kind", "procedure"),
                        tenantHeaders(companyB)), String.class);
        assertThat(crossVersion.getStatusCode().value()).isEqualTo(404);
    }

    // ── attach-to-role affects only that company's own role definition ─────

    @Test
    void attachToRoleOnlyAffectsCompanyOwnedRoleDefinition() {
        String company = createCompany("role-" + UUID.randomUUID().toString().substring(0, 8));

        ResponseEntity<String> customRole = rest.postForEntity("/api/v1/role-definitions",
                new HttpEntity<>(Map.of(
                        "key", "custom-coder",
                        "title", "Custom Coder",
                        "systemPrompt", "You write code.",
                        "outputContract", "diff"),
                        tenantHeaders(company)), String.class);
        assertThat(customRole.getStatusCode().value()).isEqualTo(201);
        String roleId = parse(customRole.getBody()).get("id").asText();

        ResponseEntity<String> createdSkill = rest.postForEntity("/api/v1/companies/" + company + "/skills",
                new HttpEntity<>(Map.of(
                        "key", "role-skill", "name", "Role skill", "description", "d",
                        "bodyMd", "body", "kind", "procedure", "tags", List.of()),
                        tenantHeaders(company)), String.class);
        String skillId = parse(createdSkill.getBody()).get("id").asText();

        ResponseEntity<String> attach = rest.postForEntity(
                "/api/v1/role-definitions/" + roleId + "/skills",
                new HttpEntity<>(Map.of("skillId", skillId), tenantHeaders(company)), String.class);
        assertThat(attach.getStatusCode().value()).isEqualTo(204);

        String agentId = hireAgent(company, "coder", "AfterAttach");
        // hired via the global 'coder' template, unaffected by the custom-role attach
        assertThat(mind(company, agentId).get("skills").findValuesAsText("key"))
                .doesNotContain("role-skill");

        // attaching to a GLOBAL template id is rejected — would leak cross-tenant
        ResponseEntity<String> roleDefs = rest.exchange("/api/v1/role-definitions", HttpMethod.GET,
                new HttpEntity<>(tenantHeaders(company)), String.class);
        String globalCoderId = null;
        for (JsonNode rd : parse(roleDefs.getBody())) {
            if (rd.get("key").asText().equals("coder") && rd.get("globalTemplate").asBoolean()) {
                globalCoderId = rd.get("id").asText();
            }
        }
        assertThat(globalCoderId).isNotNull();
        ResponseEntity<String> attachGlobal = rest.postForEntity(
                "/api/v1/role-definitions/" + globalCoderId + "/skills",
                new HttpEntity<>(Map.of("skillId", skillId), tenantHeaders(company)), String.class);
        assertThat(attachGlobal.getStatusCode().value()).isEqualTo(404);
    }
}
