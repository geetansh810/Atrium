package app.atrium.routing;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.registry.runtime.AgentHandle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
 * M2.2 Done-when (07 verbatim): a PM task spawns coder+designer children;
 * the parent is approvable only after both; {@code /tasks/{id}/flow} returns
 * the graph. Also pins the "decomposition fingerprint exact-once" test-matrix
 * row (17 §Test matrix additions) directly at the {@link TaskService#decompose}
 * layer — the LLM-tool-call path that produces the fingerprint in the first
 * place is proven end to end in execution's {@code LlmLoopRuntimeTest}.
 */
class TaskDecompositionTest extends IntegrationTestBase {

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

    private String hireAgentAndStopAutoLoop(String companyId, String name, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", name,
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

    private JsonNode getTask(String companyId, String taskId) {
        return parse(rest.exchange("/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers(companyId)), String.class).getBody());
    }

    private ResponseEntity<String> approve(String companyId, String taskId) {
        return rest.postForEntity("/api/v1/tasks/" + taskId + "/approve",
                new HttpEntity<>(headers(companyId)), String.class);
    }

    private void driveToPendingReview(UUID companyId, UUID taskId, UUID agentId) {
        workBroker.claim(companyId, taskId, agentId);
        taskService.progress(companyId, taskId, agentId, null, null, null);
        taskService.complete(companyId, taskId, agentId, "text", "decomposition plan");
    }

    // ── Done-when: PM task spawns coder+designer children; parent approvable
    //    only after both; flow endpoint returns the graph ──────────────────

    @Test
    void decomposeCreatesChildTasksAndBlocksApprovalUntilBothShip() {
        String company = createCompany("m22-decompose");
        UUID companyId = UUID.fromString(company);
        String pmAgent = hireAgentAndStopAutoLoop(company, "PM", "product");
        String coderAgent = hireAgentAndStopAutoLoop(company, "Coder", "coding");
        String designerAgent = hireAgentAndStopAutoLoop(company, "Designer", "design");

        String parentId = createTask(company, "Ship the gift box feature", "product");
        UUID parentUuid = UUID.fromString(parentId);
        driveToPendingReview(companyId, parentUuid, UUID.fromString(pmAgent));
        UUID planArtifactId = UUID.fromString(getTask(company, parentId).get("latestArtifact").get("id").asText());

        TaskService.DecompositionResult result = taskService.decompose(companyId, parentUuid,
                UUID.fromString(pmAgent), planArtifactId, List.of(
                        new TaskService.ChildTaskSpec("Build the checkout flow", "Implement it", "coding", 2),
                        new TaskService.ChildTaskSpec("Design the packaging", "Spec it", "design", 2)));
        assertThat(result.created()).isTrue();
        assertThat(result.childTaskIds()).hasSize(2);

        JsonNode codingChild = null;
        JsonNode designChild = null;
        for (UUID childId : result.childTaskIds()) {
            JsonNode child = getTask(company, childId.toString()).get("task");
            assertThat(child.get("parentTaskId").asText()).isEqualTo(parentId);
            if ("coding".equals(child.get("requiredSkill").asText())) codingChild = child;
            if ("design".equals(child.get("requiredSkill").asText())) designChild = child;
        }
        assertThat(codingChild).as("coder child created").isNotNull();
        assertThat(designChild).as("designer child created").isNotNull();

        // parent is pending_review but blocked from approval while children are open
        assertThat(getTask(company, parentId).get("task").get("status").asText()).isEqualTo("pending_review");
        assertThat(approve(company, parentId).getStatusCode().value()).isEqualTo(409);

        // audit chain: the parent's own 'decomposed' event landed alongside 'completed'
        List<String> parentEvents = jdbc.queryForList(
                "SELECT event_type FROM task_events WHERE task_id = ?::uuid ORDER BY created_at",
                String.class, parentId);
        assertThat(parentEvents).containsExactly("created", "claimed", "progress", "completed", "decomposed");

        // ship both children
        UUID codingId = UUID.fromString(codingChild.get("id").asText());
        UUID designId = UUID.fromString(designChild.get("id").asText());
        driveToPendingReview(companyId, codingId, UUID.fromString(coderAgent));
        driveToPendingReview(companyId, designId, UUID.fromString(designerAgent));
        assertThat(approve(company, codingId.toString()).getStatusCode().value()).isEqualTo(200);

        // still blocked: one child remains open
        assertThat(approve(company, parentId).getStatusCode().value()).isEqualTo(409);

        assertThat(approve(company, designId.toString()).getStatusCode().value()).isEqualTo(200);

        // both children shipped: parent is now approvable
        ResponseEntity<String> finalApprove = approve(company, parentId);
        assertThat(finalApprove.getStatusCode().value()).as(finalApprove.getBody()).isEqualTo(200);
    }

    // ── Test matrix: decomposition fingerprint exact-once ──────────────────

    @Test
    void decomposeIsExactOnceForTheSamePlanArtifact() {
        String company = createCompany("m22-exact-once");
        UUID companyId = UUID.fromString(company);
        String pmAgent = hireAgentAndStopAutoLoop(company, "PM", "product");
        hireAgentAndStopAutoLoop(company, "Coder", "coding");

        String parentId = createTask(company, "Decompose me", "product");
        UUID parentUuid = UUID.fromString(parentId);
        UUID pmUuid = UUID.fromString(pmAgent);
        driveToPendingReview(companyId, parentUuid, pmUuid);
        UUID planArtifactId = UUID.fromString(getTask(company, parentId).get("latestArtifact").get("id").asText());

        List<TaskService.ChildTaskSpec> specs =
                List.of(new TaskService.ChildTaskSpec("Do the thing", null, "coding", null));

        TaskService.DecompositionResult first =
                taskService.decompose(companyId, parentUuid, pmUuid, planArtifactId, specs);
        assertThat(first.created()).isTrue();
        assertThat(first.childTaskIds()).hasSize(1);

        // replay with the SAME plan_artifact_id (e.g. a hypothetical outbox/tool retry) —
        // must be a no-op, never a second batch of children.
        TaskService.DecompositionResult replay =
                taskService.decompose(companyId, parentUuid, pmUuid, planArtifactId, specs);
        assertThat(replay.created()).isFalse();
        assertThat(replay.childTaskIds()).isEqualTo(first.childTaskIds());

        Integer childCount = jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE parent_task_id = ?::uuid", Integer.class, parentId);
        assertThat(childCount).isEqualTo(1);

        Integer decompositionRows = jdbc.queryForObject(
                "SELECT count(*) FROM task_decompositions WHERE parent_task_id = ?::uuid", Integer.class, parentId);
        assertThat(decompositionRows).isEqualTo(1);
    }

    // ── Done-when: flow endpoint returns the graph ──────────────────────────

    @Test
    void flowEndpointReturnsNodesAndEdgesForTheWholeChain() {
        String company = createCompany("m22-flow");
        UUID companyId = UUID.fromString(company);
        String pmAgent = hireAgentAndStopAutoLoop(company, "PM", "product");
        hireAgentAndStopAutoLoop(company, "Coder", "coding");
        hireAgentAndStopAutoLoop(company, "Designer", "design");

        String parentId = createTask(company, "Ship the feature", "product");
        UUID parentUuid = UUID.fromString(parentId);
        UUID pmUuid = UUID.fromString(pmAgent);
        driveToPendingReview(companyId, parentUuid, pmUuid);
        UUID planArtifactId = UUID.fromString(getTask(company, parentId).get("latestArtifact").get("id").asText());

        TaskService.DecompositionResult result = taskService.decompose(companyId, parentUuid, pmUuid,
                planArtifactId, List.of(
                        new TaskService.ChildTaskSpec("Code it", null, "coding", null),
                        new TaskService.ChildTaskSpec("Design it", null, "design", null)));

        // querying from the PARENT and from a CHILD must return the identical graph
        for (String anchorId : List.of(parentId, result.childTaskIds().get(0).toString())) {
            JsonNode flow = parse(rest.exchange("/api/v1/tasks/" + anchorId + "/flow", HttpMethod.GET,
                    new HttpEntity<>(headers(company)), String.class).getBody());

            List<String> nodeIds = new ArrayList<>();
            flow.get("nodes").forEach(n -> nodeIds.add(n.get("taskId").asText()));
            assertThat(nodeIds).containsExactlyInAnyOrder(parentId,
                    result.childTaskIds().get(0).toString(), result.childTaskIds().get(1).toString());

            List<String> edgePairs = new ArrayList<>();
            flow.get("edges").forEach(e -> edgePairs.add(e.get("from").asText() + "->" + e.get("to").asText()));
            assertThat(edgePairs).containsExactlyInAnyOrder(
                    parentId + "->" + result.childTaskIds().get(0),
                    parentId + "->" + result.childTaskIds().get(1));

            for (JsonNode node : flow.get("nodes")) {
                if (node.get("taskId").asText().equals(parentId)) {
                    assertThat(node.get("status").asText()).isEqualTo("pending_review");
                    assertThat(node.get("agent").asText()).isEqualTo("PM");
                }
            }
        }
    }
}
