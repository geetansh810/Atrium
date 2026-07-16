package app.atrium.routing;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.execution.UsageRecorder;
import app.atrium.registry.runtime.AgentHandle;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M2.5 Done-when part 1: every flagged/pending_review task is on the
 * escalations surface (04 §Tasks {@code GET /companies/{id}/escalations}) —
 * queued/approved tasks are not.
 */
class EscalationsApiTest extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired LlmLoopRuntime runtime;
    @Autowired WorkBroker workBroker;
    @Autowired TaskService taskService;
    @Autowired UsageRecorder usageRecorder;
    @Autowired PlatformTransactionManager txManager;

    private HttpHeaders headers(String companyId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) h.set("X-Company-Id", companyId);
        return h;
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
        ResponseEntity<String> r = rest.postForEntity("/api/v1/companies",
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), headers(null)), String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(201);
        return parse(r.getBody()).get("id").asText();
    }

    private String hireAgent(String companyId, String skill) {
        ResponseEntity<String> r = rest.postForEntity("/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of("name", "Worker", "roleTemplateKey", "coder", "roleTitle", "Eng",
                        "skillTags", List.of(skill), "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId)), String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(201);
        String agentId = parse(r.getBody()).get("id").asText();
        runtime.stop(new AgentHandle(UUID.fromString(agentId), UUID.fromString(companyId)));
        return agentId;
    }

    private String createTask(String companyId, String title, String skill) {
        ResponseEntity<String> r = rest.postForEntity("/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", title, "requiredSkill", skill), headers(companyId)), String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(201);
        return parse(r.getBody()).get("task").get("id").asText();
    }

    private void completeTask(UUID companyId, UUID taskId, UUID agentId) {
        workBroker.claim(companyId, taskId, agentId);
        taskService.progress(companyId, taskId, agentId, null, null, null);
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            usageRecorder.record(companyId, agentId, taskId, 1, "anthropic", "claude-sonnet-5", 10, 10, 5L);
            taskService.complete(companyId, taskId, agentId, "text", "done");
        });
    }

    @Test
    void escalationsReturnsOnlyFlaggedAndPendingReview() {
        String company = createCompany("m25-esc");
        UUID companyId = UUID.fromString(company);
        String agentId = hireAgent(company, "coding");
        UUID agent = UUID.fromString(agentId);

        String queued = createTask(company, "Queued task", "coding");        // stays queued
        String pending = createTask(company, "Pending task", "coding");
        String flagged = createTask(company, "Flagged task", "coding");
        String approved = createTask(company, "Approved task", "coding");

        completeTask(companyId, UUID.fromString(pending), agent);            // → pending_review

        workBroker.claim(companyId, UUID.fromString(flagged), agent);
        taskService.progress(companyId, UUID.fromString(flagged), agent, null, null, null);
        taskService.flag(companyId, UUID.fromString(flagged), agent, "stuck");  // → flagged

        completeTask(companyId, UUID.fromString(approved), agent);
        taskService.approve(companyId, UUID.fromString(approved));           // → approved

        ResponseEntity<String> resp = rest.exchange("/api/v1/companies/" + company + "/escalations",
                HttpMethod.GET, new HttpEntity<>(headers(company)), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode data = parse(resp.getBody());

        List<String> ids = new java.util.ArrayList<>();
        List<String> statuses = new java.util.ArrayList<>();
        data.forEach(t -> { ids.add(t.get("id").asText()); statuses.add(t.get("status").asText()); });

        assertThat(ids).containsExactlyInAnyOrder(pending, flagged);
        assertThat(ids).doesNotContain(queued, approved);
        assertThat(statuses).allSatisfy(s -> assertThat(s).isIn("flagged", "pending_review"));
        // newest-first: 'flagged' was created after 'pending', so it sorts first
        assertThat(ids.get(0)).isEqualTo(flagged);
    }

    @Test
    void escalationsAreTenantIsolated() {
        String companyA = createCompany("m25-esc-a");
        String companyB = createCompany("m25-esc-b");
        String agentA = hireAgent(companyA, "coding");
        String pending = createTask(companyA, "A pending", "coding");
        completeTask(UUID.fromString(companyA), UUID.fromString(pending), UUID.fromString(agentA));

        JsonNode bEscalations = parse(rest.exchange("/api/v1/companies/" + companyB + "/escalations",
                HttpMethod.GET, new HttpEntity<>(headers(companyB)), String.class).getBody());
        assertThat(bEscalations).isEmpty();
    }
}
