package app.atrium.routing;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.registry.runtime.AgentHandle;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M0.6 Done-when (07 verbatim): a completed task sits in pending_review until
 * acted on; {@code GET /tasks/{id}/events} shows the full chain. The
 * feedback-reaches-the-next-prompt half is proven end-to-end in execution's
 * {@code LlmLoopRuntimeTest}, which drives a real (WireMock-backed) re-claim.
 */
class TaskApprovalGateTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskService taskService;

    @Autowired
    WorkBroker workBroker;

    @Autowired
    LlmLoopRuntime runtime;

    private final Map<String, String> tokenByCompany = new HashMap<>();

    private HttpHeaders headers(String companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) headers.setBearerAuth(tokenByCompany.get(companyId));
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
    private String createCompany(String slugPrefix) {
        String slug = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
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

    /** Hires via the API (auto-starts the real loop), then stops it immediately so
     *  this test drives claim/complete deterministically instead of racing the poll. */
    private String hireAgentAndStopAutoLoop(String companyId, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", "CoderAgent",
                        "roleTemplateKey", "coder",
                        "roleTitle", "Engineer",
                        "skillTags", List.of(skill),
                        "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        String agentId = parse(response.getBody()).get("id").asText();
        runtime.stop(new AgentHandle(UUID.fromString(agentId), UUID.fromString(companyId)));
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

    /** Drives a task to pending_review without a real LLM call. */
    private void driveToPendingReview(String companyId, String taskId, String agentId) {
        UUID cid = UUID.fromString(companyId);
        UUID tid = UUID.fromString(taskId);
        UUID aid = UUID.fromString(agentId);
        workBroker.claim(cid, tid, aid);
        taskService.progress(cid, tid, aid, null, null, null);
        taskService.complete(cid, tid, aid, "text", "artifact body");
    }

    private JsonNode getTask(String companyId, String taskId) {
        return parse(rest.exchange("/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers(companyId)), String.class).getBody());
    }

    private ResponseEntity<String> approve(String companyId, String taskId) {
        return rest.postForEntity("/api/v1/tasks/" + taskId + "/approve",
                new HttpEntity<>(headers(companyId)), String.class);
    }

    private ResponseEntity<String> reject(String companyId, String taskId, String feedback) {
        return rest.postForEntity("/api/v1/tasks/" + taskId + "/reject",
                new HttpEntity<>(Map.of("feedback", feedback), headers(companyId)), String.class);
    }

    // ── Done-when: completed task sits in pending_review until acted on ────

    @Test
    void approveShipsTheTaskAndPublishesOutbox() {
        String company = createCompany("m06-approve");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Ship it", "coding");
        driveToPendingReview(company, taskId, agentId);
        assertThat(getTask(company, taskId).get("task").get("status").asText())
                .isEqualTo("pending_review");

        ResponseEntity<String> response = approve(company, taskId);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        assertThat(parse(response.getBody()).get("status").asText()).isEqualTo("approved");
        assertThat(getTask(company, taskId).get("task").get("status").asText())
                .isEqualTo("approved");

        List<String> eventTypes = jdbc.queryForList(
                "SELECT event_type FROM task_events WHERE task_id = ?::uuid ORDER BY created_at",
                String.class, taskId);
        assertThat(eventTypes).containsExactly("created", "claimed", "progress", "completed", "approved");

        List<Map<String, Object>> outbox = jdbc.queryForList(
                "SELECT event_type FROM outbox_events WHERE payload->>'taskId' = ? "
                        + "AND event_type = 'task.approved'", taskId);
        assertThat(outbox).hasSize(1);

        // terminal: cannot approve again
        assertThat(approve(company, taskId).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void approveBlockedByOpenChildTask() {
        String company = createCompany("m06-children");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String parentId = createTask(company, "Parent", "coding");
        rest.postForEntity("/api/v1/companies/" + company + "/tasks",
                new HttpEntity<>(Map.of("title", "Child", "requiredSkill", "coding",
                        "parentTaskId", parentId), headers(company)), String.class);
        driveToPendingReview(company, parentId, agentId);

        ResponseEntity<String> response = approve(company, parentId);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(getTask(company, parentId).get("task").get("status").asText())
                .isEqualTo("pending_review");
    }

    @Test
    void approveBlockedByOpenSubtask() {
        String company = createCompany("m06-subtasks");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        ResponseEntity<String> created = rest.postForEntity(
                "/api/v1/companies/" + company + "/tasks",
                new HttpEntity<>(Map.of("title", "Checklist", "requiredSkill", "coding",
                        "subtasks", List.of(Map.of("label", "step one"))), headers(company)),
                String.class);
        String taskId = parse(created.getBody()).get("task").get("id").asText();
        driveToPendingReview(company, taskId, agentId);

        assertThat(approve(company, taskId).getStatusCode().value()).isEqualTo(409);
    }

    // ── Done-when: rejected task requeues, feedback preserved in the audit chain ──

    @Test
    void rejectRequeuesTaskAndPreservesFeedback() {
        String company = createCompany("m06-reject");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Needs rework", "coding");
        driveToPendingReview(company, taskId, agentId);

        ResponseEntity<String> response = reject(company, taskId, "Use bullet points, not prose");
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        JsonNode task = parse(response.getBody());
        assertThat(task.get("status").asText()).isEqualTo("queued");
        assertThat(task.get("assignedAgentId").isNull()).isTrue();

        List<String> eventTypes = jdbc.queryForList(
                "SELECT event_type FROM task_events WHERE task_id = ?::uuid ORDER BY created_at",
                String.class, taskId);
        assertThat(eventTypes).containsExactly(
                "created", "claimed", "progress", "completed", "rejected", "requeued");

        JsonNode events = parse(rest.exchange("/api/v1/tasks/" + taskId + "/events", HttpMethod.GET,
                new HttpEntity<>(headers(company)), String.class).getBody()).get("data");
        JsonNode rejectedEvent = null;
        for (JsonNode e : events) {
            if ("rejected".equals(e.get("eventType").asText())) rejectedEvent = e;
        }
        assertThat(rejectedEvent).as("rejected event present in the full chain").isNotNull();
        assertThat(rejectedEvent.get("payload").get("feedback").asText())
                .isEqualTo("Use bullet points, not prose");

        // re-claim gets a fresh attempt + idempotency key; feedback is readable for the retry
        UUID cid = UUID.fromString(company);
        UUID tid = UUID.fromString(taskId);
        UUID aid = UUID.fromString(agentId);
        var reclaimed = workBroker.claim(cid, tid, aid);
        assertThat(reclaimed.getAttempt()).isEqualTo(2);
        assertThat(taskService.latestRejectionFeedback(cid, tid))
                .contains("Use bullet points, not prose");
    }

    @Test
    void rejectRequiresFeedbackBody() {
        String company = createCompany("m06-reject-validation");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Needs rework", "coding");
        driveToPendingReview(company, taskId, agentId);

        ResponseEntity<String> response = rest.postForEntity("/api/v1/tasks/" + taskId + "/reject",
                new HttpEntity<>(Map.of(), headers(company)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
