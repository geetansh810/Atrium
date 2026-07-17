package app.atrium.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.atrium.IntegrationTestBase;
import app.atrium.common.ForbiddenException;
import app.atrium.common.TenantContext;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.registry.runtime.AgentHandle;
import app.atrium.routing.domain.Task;
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
 * M4.1/M4.2 Done-when (07 Phase 4): a legal/HR-role task is structurally
 * impossible to approve without a named human sign-off (proven by test, the
 * card's own literal Done-when text); the completed event on any task carries
 * enough model/prompt-version/inputs provenance to be a full reconstructable
 * audit trail on its own, readable by an outsider (someone with nothing but
 * an authenticated read against {@code GET /tasks/{id}/events}).
 */
class ComplianceGateTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    JdbcTemplate jdbc = adminJdbc();

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

    /** Hires against a global role TEMPLATE key (e.g. 'legal'/'hr'/'coder'), then stops
     *  the auto-started loop so this test drives claim/complete deterministically. */
    private String hireAgainstTemplate(String companyId, String roleTemplateKey, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", roleTemplateKey + "Agent",
                        "roleTemplateKey", roleTemplateKey,
                        "roleTitle", roleTemplateKey,
                        "skillTags", List.of(skill),
                        "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        String agentId = parse(response.getBody()).get("id").asText();
        runtime.stop(new AgentHandle(UUID.fromString(agentId), UUID.fromString(companyId)));
        return agentId;
    }

    private String createTask(String companyId, String title, String description, String skill) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (description != null) body.put("description", description);
        body.put("requiredSkill", skill);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(body, headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("task").get("id").asText();
    }

    /** Drives a task to pending_review without a real LLM call — same idiom as
     *  TaskApprovalGateTest's driveToPendingReview (M3.2: bare service calls bind
     *  TenantContext.runAsSystem for RLS, same as LlmLoopRuntime does for real). */
    private void driveToPendingReview(String companyId, String taskId, String agentId) {
        UUID cid = UUID.fromString(companyId);
        UUID tid = UUID.fromString(taskId);
        UUID aid = UUID.fromString(agentId);
        TenantContext.runAsSystem(cid, () -> {
            workBroker.claim(cid, tid, aid);
            taskService.progress(cid, tid, aid, null, null, null);
            taskService.complete(cid, tid, aid, "text", "artifact body");
        });
    }

    private ResponseEntity<String> approveViaHttp(String companyId, String taskId) {
        return rest.postForEntity("/api/v1/tasks/" + taskId + "/approve",
                new HttpEntity<>(headers(companyId)), String.class);
    }

    // ── M4.1: legal/HR-role output cannot be approved by anything but a named human ──

    @Test
    void legalRoleTaskCannotBeApprovedByAgentOrSystemActorOnlyByANamedHuman() {
        String company = createCompany("m41-legal");
        String agentId = hireAgainstTemplate(company, "legal", "legal-review");
        String taskId = createTask(company, "Review vendor NDA", "Draft NDA text attached.", "legal-review");
        driveToPendingReview(company, taskId, agentId);

        UUID cid = UUID.fromString(company);
        UUID tid = UUID.fromString(taskId);

        // Structurally impossible without a named human: a system-actor approve is rejected...
        assertThatThrownBy(() -> TenantContext.runAsSystem(cid, () -> taskService.approve(cid, tid)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM tasks WHERE id = ?::uuid", String.class, taskId))
                .isEqualTo("pending_review");

        // ...and the same call, with a real authenticated human bound (the HTTP path,
        // which always carries a JWT-bound userId since M3.1), succeeds.
        ResponseEntity<String> response = approveViaHttp(company, taskId);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        assertThat(parse(response.getBody()).get("status").asText()).isEqualTo("approved");
    }

    @Test
    void hrRoleTaskAlsoRequiresANamedHumanApprover() {
        String company = createCompany("m41-hr");
        String agentId = hireAgainstTemplate(company, "hr", "hr-review");
        String taskId = createTask(company, "Draft PIP language",
                "Employee missed 3 consecutive deadlines, see attached log.", "hr-review");
        driveToPendingReview(company, taskId, agentId);

        UUID cid = UUID.fromString(company);
        UUID tid = UUID.fromString(taskId);
        assertThatThrownBy(() -> TenantContext.runAsSystem(cid, () -> taskService.approve(cid, tid)))
                .isInstanceOf(ForbiddenException.class);

        assertThat(approveViaHttp(company, taskId).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void ungatedRoleIsUnaffectedByTheComplianceGate() {
        String company = createCompany("m41-coder");
        String agentId = hireAgainstTemplate(company, "coder", "coding");
        String taskId = createTask(company, "Ship it", null, "coding");
        driveToPendingReview(company, taskId, agentId);

        // 'coder' has review_required=false — a system-actor approve is NOT blocked,
        // proving the gate is data-driven per role, not a blanket restriction (roles are data).
        UUID cid = UUID.fromString(company);
        UUID tid = UUID.fromString(taskId);
        Task approved = TenantContext.callAsSystem(cid, () -> taskService.approve(cid, tid));
        assertThat(approved.getStatus()).isEqualTo("approved");
    }

    // ── M4.2: full reconstructable audit trail on the completed event ──

    @Test
    void completedEventCarriesFullAuditPayloadForReconstruction() {
        String company = createCompany("m42-hr");
        String agentId = hireAgainstTemplate(company, "hr", "hr-review");
        String taskId = createTask(company, "Draft interview questions",
                "Role: backend engineer. Focus on system design.", "hr-review");

        UUID cid = UUID.fromString(company);
        UUID tid = UUID.fromString(taskId);
        UUID aid = UUID.fromString(agentId);
        UUID hrRoleDefId = UUID.fromString(jdbc.queryForObject(
                "SELECT id FROM role_definitions WHERE company_id IS NULL AND key = 'hr'", String.class));

        TenantContext.runAsSystem(cid, () -> {
            workBroker.claim(cid, tid, aid);
            taskService.progress(cid, tid, aid, null, null, null);
            taskService.complete(cid, tid, aid, "text", "1. Tell me about a system you designed...",
                    new TaskService.CompletionAudit("anthropic", "claude-sonnet-5", hrRoleDefId, 1,
                            "Prior draft was too generic — ask about tradeoffs"));
        });

        // Readable by an outsider: anyone with an authenticated read against the ordinary
        // events endpoint, no raw DB access, no cross-referencing another table required.
        JsonNode events = parse(rest.exchange("/api/v1/tasks/" + taskId + "/events", HttpMethod.GET,
                new HttpEntity<>(headers(company)), String.class).getBody()).get("data");
        JsonNode completedPayload = null;
        for (JsonNode e : events) {
            if ("completed".equals(e.get("eventType").asText())) completedPayload = e.get("payload");
        }
        assertThat(completedPayload).as("completed event present").isNotNull();
        assertThat(completedPayload.get("model").asText()).isEqualTo("anthropic/claude-sonnet-5");
        assertThat(completedPayload.get("roleDefinitionId").asText()).isEqualTo(hrRoleDefId.toString());
        assertThat(completedPayload.get("promptVersion").asInt()).isEqualTo(1);
        JsonNode inputs = completedPayload.get("inputs");
        assertThat(inputs.get("title").asText()).isEqualTo("Draft interview questions");
        assertThat(inputs.get("description").asText()).isEqualTo("Role: backend engineer. Focus on system design.");
        assertThat(inputs.get("feedback").asText()).isEqualTo("Prior draft was too generic — ask about tradeoffs");

        // The role_definitions row addressed by roleDefinitionId+promptVersion is itself
        // immutable — the exact system_prompt/output_contract used is independently readable.
        String systemPrompt = jdbc.queryForObject(
                "SELECT system_prompt FROM role_definitions WHERE id = ?::uuid", String.class,
                hrRoleDefId.toString());
        assertThat(systemPrompt).contains("HR specialist");
    }

    @Test
    void plainCompletionWithNoAuditStillWorksUnaffected() {
        String company = createCompany("m42-echo");
        String agentId = hireAgainstTemplate(company, "coder", "coding");
        String taskId = createTask(company, "Ship it", null, "coding");
        driveToPendingReview(company, taskId, agentId);

        JsonNode events = parse(rest.exchange("/api/v1/tasks/" + taskId + "/events", HttpMethod.GET,
                new HttpEntity<>(headers(company)), String.class).getBody()).get("data");
        for (JsonNode e : events) {
            if ("completed".equals(e.get("eventType").asText())) {
                assertThat(e.get("payload").has("model")).isFalse();
                assertThat(e.get("payload").has("inputs")).isFalse();
            }
        }
    }
}
