package app.atrium.accountability;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.common.TenantContext;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.execution.UsageRecorder;
import app.atrium.registry.runtime.AgentHandle;
import app.atrium.routing.TaskService;
import app.atrium.routing.WorkBroker;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M2.3 Done-when (07 + 17): {@code agent_stats_daily} accumulates real counts
 * from completed/approved/rejected tasks via the durable {@link
 * StatsRollupWorker} consumer. {@link StatsRollupWorker#pollOnce} is called
 * directly, same precedent as {@code LearningPipelineTest}/{@code
 * EventCursorWorkerTest} — the scheduled tick is disabled in the test profile.
 */
class StatsRollupTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    JdbcTemplate jdbc = adminJdbc();

    @Autowired
    LlmLoopRuntime runtime;

    @Autowired
    WorkBroker workBroker;

    @Autowired
    TaskService taskService;

    @Autowired
    UsageRecorder usageRecorder;

    @Autowired
    StatsRollupWorker statsRollupWorker;

    @Autowired
    PlatformTransactionManager txManager;

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

    private String hireAgent(String companyId, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", "Worker",
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

    /** Same reset-to-current-max-id idiom as LearningPipelineTest — the Testcontainers
     *  Postgres is shared across the whole suite, so this test must only see events it
     *  creates itself. */
    private void resetCursorToNow() {
        Long baseline = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox_events", Long.class);
        jdbc.update("""
                INSERT INTO event_consumers(consumer_name, last_event_id) VALUES (?, ?)
                ON CONFLICT (consumer_name) DO UPDATE SET last_event_id = EXCLUDED.last_event_id
                """, StatsRollupWorker.CONSUMER_NAME, baseline);
    }

    private Map<String, Object> statsRow(String companyId, String agentId, String skill) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT tasks_completed, tasks_approved, tasks_rejected, tokens_spent, cost_micro_usd
                FROM agent_stats_daily
                WHERE company_id = ?::uuid AND agent_id = ?::uuid AND skill = ?
                  AND day = (now() AT TIME ZONE 'UTC')::date
                """, companyId, agentId, skill);
        assertThat(rows).as("agent_stats_daily row for company=%s agent=%s skill=%s", companyId, agentId, skill)
                .hasSize(1);
        return rows.get(0);
    }

    @Test
    void completeApproveAndRejectAccumulateIntoOneRollupRow() {
        resetCursorToNow();
        String company = createCompany("m23-rollup");
        String agentId = hireAgent(company, "coding");
        String task1 = createTask(company, "Task one", "coding");
        String task2 = createTask(company, "Task two", "coding");

        UUID companyId = UUID.fromString(company);
        UUID agentUuid = UUID.fromString(agentId);
        UUID task1Id = UUID.fromString(task1);
        UUID task2Id = UUID.fromString(task2);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // M3.2: bare service calls (no wrapping HTTP request) — bind the
        // tenant for RLS the same way LlmLoopRuntime does (08 §Security rule 6).
        TenantContext.runAsSystem(companyId, () -> {
            // task1: claim → complete (100 tokens, 40 µUSD) → approve
            workBroker.claim(companyId, task1Id, agentUuid);
            taskService.progress(companyId, task1Id, agentUuid, null, null, null);
            tx.executeWithoutResult(status -> {
                usageRecorder.record(companyId, agentUuid, task1Id, 1, "anthropic", "claude-sonnet-5", 60, 40, 40L);
                taskService.complete(companyId, task1Id, agentUuid, "text", "done");
            });
            taskService.approve(companyId, task1Id);

            // task2: claim → complete (50 tokens, 15 µUSD) → reject
            workBroker.claim(companyId, task2Id, agentUuid);
            taskService.progress(companyId, task2Id, agentUuid, null, null, null);
            tx.executeWithoutResult(status -> {
                usageRecorder.record(companyId, agentUuid, task2Id, 1, "anthropic", "claude-sonnet-5", 30, 20, 15L);
                taskService.complete(companyId, task2Id, agentUuid, "text", "attempt one");
            });
            taskService.reject(companyId, task2Id, "needs more detail");
        });

        statsRollupWorker.pollOnce(50);

        Map<String, Object> row = statsRow(company, agentId, "coding");
        assertThat(((Number) row.get("tasks_completed")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("tasks_approved")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("tasks_rejected")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("tokens_spent")).longValue()).isEqualTo(100 + 50);
        assertThat(((Number) row.get("cost_micro_usd")).longValue()).isEqualTo(40 + 15);

        // polling again with nothing new is a safe no-op (no double count)
        statsRollupWorker.pollOnce(50);
        Map<String, Object> rowAfterExtraPoll = statsRow(company, agentId, "coding");
        assertThat(rowAfterExtraPoll).isEqualTo(row);
    }

    @Test
    void differentSkillsOnTheSameDayGetSeparateRows() {
        resetCursorToNow();
        String company = createCompany("m23-skills");
        String agentId = hireAgent(company, "coding");

        // Hire a second skill onto the same agent isn't part of the DTO surface used here —
        // instead prove the (agent, skill, day) grain with two agents on two skills.
        String agent2Id = hireAgent(company, "content");
        String task1 = createTask(company, "Code task", "coding");
        String task2 = createTask(company, "Content task", "content");

        UUID companyId = UUID.fromString(company);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        UUID task1Id = UUID.fromString(task1);
        UUID task2Id = UUID.fromString(task2);
        // M3.2: bare service calls (no wrapping HTTP request) — bind the
        // tenant for RLS the same way LlmLoopRuntime does (08 §Security rule 6).
        TenantContext.runAsSystem(companyId, () -> {
            workBroker.claim(companyId, task1Id, UUID.fromString(agentId));
            taskService.progress(companyId, task1Id, UUID.fromString(agentId), null, null, null);
            tx.executeWithoutResult(status -> {
                usageRecorder.record(companyId, UUID.fromString(agentId), task1Id, 1,
                        "anthropic", "claude-sonnet-5", 10, 10, 5L);
                taskService.complete(companyId, task1Id, UUID.fromString(agentId), "text", "done");
            });

            workBroker.claim(companyId, task2Id, UUID.fromString(agent2Id));
            taskService.progress(companyId, task2Id, UUID.fromString(agent2Id), null, null, null);
            tx.executeWithoutResult(status -> {
                usageRecorder.record(companyId, UUID.fromString(agent2Id), task2Id, 1,
                        "anthropic", "claude-sonnet-5", 20, 20, 8L);
                taskService.complete(companyId, task2Id, UUID.fromString(agent2Id), "text", "done");
            });
        });

        statsRollupWorker.pollOnce(50);

        assertThat(((Number) statsRow(company, agentId, "coding").get("tasks_completed")).intValue()).isEqualTo(1);
        assertThat(((Number) statsRow(company, agent2Id, "content").get("tasks_completed")).intValue()).isEqualTo(1);
    }
}
