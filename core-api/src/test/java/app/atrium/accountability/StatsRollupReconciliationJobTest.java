package app.atrium.accountability;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.registry.runtime.AgentHandle;
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

/**
 * M2.3's nightly drift-correcting recompute: seeds raw {@code outbox_events} +
 * {@code usage_records} rows directly (bypassing {@link StatsRollupWorker}
 * entirely) plus a deliberately WRONG pre-existing {@code agent_stats_daily}
 * row, then proves {@link StatsRollupReconciliationJob#reconcile} replaces it
 * with the correct recomputed value — the actual "ground truth" behavior the
 * job exists for, not just a re-run of the incremental path.
 */
class StatsRollupReconciliationJobTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StatsRollupReconciliationJob reconciliationJob;

    @Autowired
    LlmLoopRuntime runtime;

    private final Map<String, String> tokenByCompany = new HashMap<>();

    private HttpHeaders headers(String companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) headers.setBearerAuth(tokenByCompany.get(companyId));
        return headers;
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
        try {
            var body = json.readTree(response.getBody());
            String companyId = body.get("companyId").asText();
            tokenByCompany.put(companyId, body.get("token").asText());
            return companyId;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String hireAgent(String companyId) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", "Worker", "roleTemplateKey", "coder", "roleTitle", "Engineer",
                        "skillTags", List.of("coding"), "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        String agentId;
        try {
            agentId = json.readTree(response.getBody()).get("id").asText();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        runtime.stop(new AgentHandle(UUID.fromString(agentId), UUID.fromString(companyId)));
        return agentId;
    }

    private String createTask(String companyId) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", "T", "requiredSkill", "coding"), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        try {
            return json.readTree(response.getBody()).get("task").get("id").asText();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void seedOutboxEvent(String companyId, String taskId, String agentId,
                                 String eventType, int attempt) {
        String payload = """
                {"taskId":"%s","agentId":"%s","requiredSkill":"coding","attempt":%d,"status":"x"}
                """.formatted(taskId, agentId, attempt);
        jdbc.update("""
                INSERT INTO outbox_events (company_id, topic, event_type, payload, created_at)
                VALUES (?::uuid, 'test-topic', ?, ?::jsonb, now())
                """, companyId, eventType, payload);
    }

    private void seedUsageRecord(String companyId, String agentId, String taskId, String idempotencyKey) {
        jdbc.update("""
                INSERT INTO usage_records
                    (company_id, agent_id, task_id, provider, model, tokens_in, tokens_out,
                     cost_micro_usd, idempotency_key)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'anthropic', 'claude-sonnet-5', 200, 100, 75, ?)
                """, companyId, agentId, taskId, idempotencyKey);
    }

    @Test
    void reconcileReplacesADriftedRowWithTheTrueRecomputedValue() {
        String company = createCompany("m23-reconcile");
        String agentId = hireAgent(company);
        UUID taskId = UUID.fromString(createTask(company));

        seedOutboxEvent(company, taskId.toString(), agentId, "task.completed", 1);
        seedOutboxEvent(company, taskId.toString(), agentId, "task.approved", 1);
        seedUsageRecord(company, agentId, taskId.toString(), taskId + ":1");

        // Deliberately wrong pre-existing row — never touched by StatsRollupWorker in this test.
        jdbc.update("""
                INSERT INTO agent_stats_daily
                    (company_id, agent_id, skill, day, tasks_completed, tasks_approved,
                     tasks_rejected, tokens_spent, cost_micro_usd)
                VALUES (?::uuid, ?::uuid, 'coding', (now() AT TIME ZONE 'UTC')::date, 999, 999, 999, 999, 999)
                """, company, agentId);

        reconciliationJob.reconcile();

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT tasks_completed, tasks_approved, tasks_rejected, tokens_spent, cost_micro_usd
                FROM agent_stats_daily
                WHERE company_id = ?::uuid AND agent_id = ?::uuid AND skill = 'coding'
                  AND day = (now() AT TIME ZONE 'UTC')::date
                """, company, agentId);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(((Number) row.get("tasks_completed")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("tasks_approved")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("tasks_rejected")).intValue()).isEqualTo(0);
        assertThat(((Number) row.get("tokens_spent")).longValue()).isEqualTo(300);
        assertThat(((Number) row.get("cost_micro_usd")).longValue()).isEqualTo(75);
    }
}
