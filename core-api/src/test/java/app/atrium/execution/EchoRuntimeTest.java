package app.atrium.execution;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M-AR1 Done-when: hiring an agent with {@code runtimeType='echo'} claims and
 * completes a task through the completely unmodified claim/complete pipeline
 * — the "any agent type without breaking base schema" proof (13 §3.3). This
 * class is the ONLY test surface for that proof; routing/, registry/ (minus
 * seed) and every migration are untouched by this milestone (verify with
 * {@code git diff --stat routing/ registry/ src/main/resources/db/migration/}
 * from repo root — should be empty).
 */
class EchoRuntimeTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EchoRuntime runtime;

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
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), headers(null)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private ResponseEntity<String> hireRaw(String companyId, Map<String, Object> body) {
        return rest.postForEntity("/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(body, headers(companyId)), String.class);
    }

    /** Hires an echo-runtime agent via the real API (which auto-starts its loop), then
     *  stops that loop immediately so the test drives runOnce() deterministically. */
    private String hireEchoAgent(String companyId, String skill) {
        ResponseEntity<String> response = hireRaw(companyId, Map.of(
                "name", "EchoAgent",
                "roleTemplateKey", "coder",
                "roleTitle", "Echo Worker",
                "skillTags", List.of(skill),
                "modelProvider", "none",
                "modelName", "none",
                "runtimeType", "echo"));
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        String agentId = parse(response.getBody()).get("id").asText();
        runtime.stop(new app.atrium.registry.runtime.AgentHandle(
                UUID.fromString(agentId), UUID.fromString(companyId)));
        return agentId;
    }

    private String createTask(String companyId, String title, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", title, "requiredSkill", skill), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("task").get("id").asText();
    }

    @Test
    void hiredEchoAgentClaimsAndCompletesThroughTheUnmodifiedPipeline() {
        String company = createCompany("m-ar1");
        String agentId = hireEchoAgent(company, "coding");
        String taskId = createTask(company, "Any task at all", "coding");

        runtime.runOnce(UUID.fromString(company), UUID.fromString(agentId));

        JsonNode detail = parse(rest.exchange("/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers(company)), String.class).getBody());
        assertThat(detail.get("task").get("status").asText()).isEqualTo("pending_review");
        assertThat(detail.get("task").get("progress").asInt()).isEqualTo(100);
        assertThat(detail.get("latestArtifact")).isNotNull();
        assertThat(detail.get("latestArtifact").get("kind").asText()).isEqualTo("text");
        assertThat(detail.get("latestArtifact").get("content").asText())
                .contains("Echo runtime");

        // full audit chain, exactly like any other runtime — nothing routing-side
        // needed changing for a brand new agent type to produce it
        List<String> eventTypes = jdbc.queryForList(
                "SELECT event_type FROM task_events WHERE task_id = ?::uuid ORDER BY created_at",
                String.class, taskId);
        assertThat(eventTypes).containsExactly("created", "claimed", "progress", "completed");

        // approve works unmodified too
        ResponseEntity<String> approve = rest.postForEntity("/api/v1/tasks/" + taskId + "/approve",
                new HttpEntity<>(headers(company)), String.class);
        assertThat(approve.getStatusCode().value()).as(approve.getBody()).isEqualTo(200);
    }

    @Test
    void unknownRuntimeConfigKeyRejectedAtHireTime() {
        String company = createCompany("m-ar1-badcfg");
        ResponseEntity<String> response = hireRaw(company, Map.of(
                "name", "EchoAgent",
                "roleTemplateKey", "coder",
                "roleTitle", "Echo Worker",
                "skillTags", List.of("coding"),
                "modelProvider", "none",
                "modelName", "none",
                "runtimeType", "echo",
                "runtimeConfig", Map.of("bogusKey", 1)));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(response.getBody()).get("fieldErrors").has("runtimeConfig.bogusKey")).isTrue();
    }

    @Test
    void unknownRuntimeTypeRejectedAtHireTime() {
        String company = createCompany("m-ar1-badtype");
        ResponseEntity<String> response = hireRaw(company, Map.of(
                "name", "GhostAgent",
                "roleTemplateKey", "coder",
                "roleTitle", "Ghost Worker",
                "skillTags", List.of("coding"),
                "modelProvider", "none",
                "modelName", "none",
                "runtimeType", "not_a_real_runtime"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(response.getBody()).get("fieldErrors").has("runtimeType")).isTrue();
    }
}
