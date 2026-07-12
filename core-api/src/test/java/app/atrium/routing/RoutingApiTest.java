package app.atrium.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.atrium.IntegrationTestBase;
import app.atrium.eventbus.OutboxWriter;
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
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * M0.3 Done-when: second company sees nothing (isolation) + creating a task
 * writes exactly one task_events row AND one outbox_events row in one tx.
 */
class RoutingApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxWriter outboxWriter;

    // ── helpers ────────────────────────────────────────────────────────────

    private HttpHeaders headers(String companyId, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) headers.set("X-Company-Id", companyId);
        if (userId != null) headers.set("X-User-Id", userId);
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
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), headers(null, null)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private void hireAgent(String companyId, String name, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", name,
                        "roleTemplateKey", "coder",
                        "roleTitle", "Engineer",
                        "skillTags", List.of(skill),
                        "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId, null)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    private ResponseEntity<String> postTask(String companyId, String userId, Map<String, Object> body) {
        return rest.postForEntity("/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(body, headers(companyId, userId)), String.class);
    }

    private ResponseEntity<String> getWithTenant(String url, String companyId) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(companyId, null)),
                String.class);
    }

    private Map<String, Object> taskBody(String title, String skill) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("requiredSkill", skill);
        return body;
    }

    /** No users API until Phase 3 — seed the human directly (tasks.created_by_user_id FK). */
    private String createUser(String companyId) {
        return jdbc.queryForObject(
                "INSERT INTO users (company_id, display_name, email) VALUES (?::uuid, ?, ?) RETURNING id",
                String.class, companyId, "Test User", "user-" + UUID.randomUUID() + "@test.local");
    }

    // ── Done-when: one task_events row AND one outbox_events row, same tx ──

    @Test
    void createTaskWritesExactlyOneAuditRowAndOneOutboxRow() {
        String company = createCompany("m03");
        hireAgent(company, "CoderAgent", "coding");
        String userId = createUser(company);

        Map<String, Object> body = taskBody("Build the widget", "coding");
        body.put("description", "A widget");
        body.put("priority", 2);
        body.put("subtasks", List.of(Map.of("label", "design"), Map.of("label", "implement")));
        ResponseEntity<String> response = postTask(company, userId, body);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);

        JsonNode task = parse(response.getBody()).get("task");
        String taskId = task.get("id").asText();
        assertThat(task.get("status").asText()).isEqualTo("queued");
        assertThat(task.get("billingTaskId").asText()).as("root bills to itself").isEqualTo(taskId);
        assertThat(task.get("requestDepth").asInt()).isZero();
        assertThat(task.get("attempt").asInt()).isZero();

        JsonNode subtasks = parse(response.getBody()).get("subtasks");
        assertThat(subtasks).hasSize(2);
        assertThat(subtasks.get(0).get("position").asInt()).isZero();
        assertThat(subtasks.get(1).get("label").asText()).isEqualTo("implement");

        // Exactly one audit row, with the acting user
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT event_type, actor FROM task_events WHERE task_id = ?::uuid", taskId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("event_type")).isEqualTo("created");
        assertThat(events.get(0).get("actor")).isEqualTo("user:" + userId);

        // Exactly one outbox row, correct topic/type, pending relay
        List<Map<String, Object>> outbox = jdbc.queryForList(
                "SELECT topic, event_type, payload, published_at FROM outbox_events "
                        + "WHERE payload->>'taskId' = ?", taskId);
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).get("topic"))
                .isEqualTo("atrium/v1/" + company + "/task/" + taskId);
        assertThat(outbox.get(0).get("event_type")).isEqualTo("task.created");
        assertThat(outbox.get(0).get("published_at")).isNull();
        assertThat(parse(outbox.get(0).get("payload").toString()).get("status").asText())
                .isEqualTo("queued");
    }

    /** Structural half of "in one tx": the outbox refuses to be written outside one. */
    @Test
    void outboxWriterOutsideATransactionIsAHardError() {
        assertThatThrownBy(() -> outboxWriter.append(UUID.randomUUID(),
                "atrium/v1/x/task/y", "task.created", json.createObjectNode()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ── billing chain: child copies root's billing id, depth increments ─────

    @Test
    void childTaskInheritsBillingChainFromParent() {
        String company = createCompany("chain");
        hireAgent(company, "CoderAgent", "coding");

        String parentId = parse(postTask(company, null, taskBody("Parent", "coding")).getBody())
                .get("task").get("id").asText();

        Map<String, Object> childBody = taskBody("Child", "coding");
        childBody.put("parentTaskId", parentId);
        JsonNode child = parse(postTask(company, null, childBody).getBody()).get("task");

        assertThat(child.get("parentTaskId").asText()).isEqualTo(parentId);
        assertThat(child.get("billingTaskId").asText()).as("bills to the root").isEqualTo(parentId);
        assertThat(child.get("requestDepth").asInt()).isEqualTo(1);
        // created without a user header → system actor
        JsonNode events = parse(getWithTenant(
                "/api/v1/tasks/" + child.get("id").asText() + "/events", company).getBody());
        assertThat(events.get("data")).hasSize(1);
        assertThat(events.get("data").get(0).get("actor").asText()).isEqualTo("system");
    }

    // ── skill is validated against the roster, never a switch ───────────────

    @Test
    void unknownSkillIsRejectedWithFieldError() {
        String company = createCompany("noskill");
        hireAgent(company, "CoderAgent", "coding");

        ResponseEntity<String> response = postTask(company, null, taskBody("Nope", "juggling"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(parse(response.getBody()).get("fieldErrors").has("requiredSkill")).isTrue();
        // nothing half-written
        Integer tasks = jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE company_id = ?::uuid", Integer.class, company);
        assertThat(tasks).isZero();
    }

    // ── pagination envelope: {data, nextCursor}, keyset walk, filters ───────

    @Test
    void listPaginatesWithCursorAndFilters() {
        String company = createCompany("page");
        hireAgent(company, "CoderAgent", "coding");
        for (int i = 1; i <= 5; i++) {
            assertThat(postTask(company, null, taskBody("Task " + i, "coding"))
                    .getStatusCode().value()).isEqualTo(201);
        }

        String base = "/api/v1/companies/" + company + "/tasks";
        List<String> seen = new ArrayList<>();
        JsonNode page = parse(getWithTenant(base + "?limit=2", company).getBody());
        assertThat(page.get("data")).hasSize(2);
        assertThat(page.hasNonNull("nextCursor")).isTrue();
        page.get("data").forEach(t -> seen.add(t.get("id").asText()));
        // newest first
        assertThat(page.get("data").get(0).get("title").asText()).isEqualTo("Task 5");

        while (page.hasNonNull("nextCursor")) {
            page = parse(getWithTenant(base + "?limit=2&cursor="
                    + page.get("nextCursor").asText(), company).getBody());
            page.get("data").forEach(t -> seen.add(t.get("id").asText()));
        }
        assertThat(seen).as("cursor walk covers all tasks exactly once")
                .hasSize(5).doesNotHaveDuplicates();

        JsonNode filtered = parse(getWithTenant(base + "?status=queued&skill=coding", company)
                .getBody());
        assertThat(filtered.get("data")).hasSize(5);
        assertThat(parse(getWithTenant(base + "?status=approved", company).getBody()).get("data"))
                .isEmpty();
        assertThat(parse(getWithTenant(base + "?skill=juggling", company).getBody()).get("data"))
                .isEmpty();

        // bad inputs are 400s, not 500s
        assertThat(getWithTenant(base + "?cursor=garbage", company).getStatusCode().value())
                .isEqualTo(400);
        assertThat(getWithTenant(base + "?status=bogus", company).getStatusCode().value())
                .isEqualTo(400);
        assertThat(getWithTenant(base + "?view=everything", company).getStatusCode().value())
                .isEqualTo(400);
    }

    // ── Done-when: second company sees nothing ───────────────────────────────

    @Test
    void secondCompanySeesNothingOfTheFirst() {
        String companyA = createCompany("task-iso-a");
        String companyB = createCompany("task-iso-b");
        hireAgent(companyA, "SecretCoder", "coding");
        String taskA = parse(postTask(companyA, null, taskBody("Secret work", "coding")).getBody())
                .get("task").get("id").asText();

        // B's board is empty
        JsonNode listB = parse(getWithTenant(
                "/api/v1/companies/" + companyB + "/tasks", companyB).getBody());
        assertThat(listB.get("data")).isEmpty();

        // B cannot read A's task, its events, or A's board — 404/empty, never data
        assertThat(getWithTenant("/api/v1/tasks/" + taskA, companyB)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(getWithTenant("/api/v1/tasks/" + taskA + "/events", companyB)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(getWithTenant("/api/v1/companies/" + companyA + "/tasks", companyB)
                .getStatusCode().value()).isEqualTo(404);

        // B cannot create tasks on A's board
        assertThat(postTask(companyA, null, taskBody("Sneaky", "coding")).getStatusCode().value())
                .as("A's own header still works").isEqualTo(201);
        ResponseEntity<String> cross = rest.postForEntity(
                "/api/v1/companies/" + companyA + "/tasks",
                new HttpEntity<>(taskBody("Hijack", "coding"), headers(companyB, null)),
                String.class);
        assertThat(cross.getStatusCode().value()).isEqualTo(404);
    }

    // ── detail + events read back ────────────────────────────────────────────

    @Test
    void taskDetailReturnsSubtasksAndEventsEndpointReturnsAudit() {
        String company = createCompany("detail");
        hireAgent(company, "CoderAgent", "coding");

        Map<String, Object> body = taskBody("Detailed", "coding");
        body.put("subtasks", List.of(Map.of("label", "step one")));
        String taskId = parse(postTask(company, null, body).getBody())
                .get("task").get("id").asText();

        JsonNode detail = parse(getWithTenant("/api/v1/tasks/" + taskId, company).getBody());
        assertThat(detail.get("task").get("title").asText()).isEqualTo("Detailed");
        assertThat(detail.get("subtasks")).hasSize(1);
        assertThat(detail.get("subtasks").get(0).get("state").asText()).isEqualTo("todo");
        assertThat(detail.get("latestArtifact").isNull()).as("artifacts land at M0.5b").isTrue();

        JsonNode events = parse(getWithTenant("/api/v1/tasks/" + taskId + "/events", company)
                .getBody());
        assertThat(events.get("data")).hasSize(1);
        JsonNode created = events.get("data").get(0);
        assertThat(created.get("eventType").asText()).isEqualTo("created");
        assertThat(created.get("payload").get("title").asText()).isEqualTo("Detailed");

        // unknown parent → 404, nothing created
        Map<String, Object> orphan = taskBody("Orphan", "coding");
        orphan.put("parentTaskId", UUID.randomUUID().toString());
        assertThat(postTask(company, null, orphan).getStatusCode().value()).isEqualTo(404);
    }
}
