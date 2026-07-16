package app.atrium.accountability;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.execution.UsageRecorder;
import app.atrium.registry.runtime.AgentHandle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M0.7 Done-when (07 verbatim + 17's soft-tier/auto-pause addition): low cap
 * + several tasks → claim blocked with a visible flagged event at the cap;
 * threshold event fires exactly once; over-cap agent shows paused in roster
 * and resumes on unpause.
 */
class BudgetEnforcementTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LlmLoopRuntime runtime;

    @Autowired
    UsageRecorder usageRecorder;

    @Autowired
    PlatformTransactionManager txManager;

    private final Map<String, String> tokenByCompany = new HashMap<>();

    private HttpHeaders headers(String companyId, String agentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) headers.setBearerAuth(tokenByCompany.get(companyId));
        if (agentId != null) headers.set("X-Agent-Id", agentId);
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

    private String hireAgentAndStopAutoLoop(String companyId, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", "Worker",
                        "roleTemplateKey", "coder",
                        "roleTitle", "Engineer",
                        "skillTags", List.of(skill),
                        "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId, null)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        String agentId = parse(response.getBody()).get("id").asText();
        runtime.stop(new AgentHandle(UUID.fromString(agentId), UUID.fromString(companyId)));
        return agentId;
    }

    private String createTask(String companyId, String title, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", title, "requiredSkill", skill),
                        headers(companyId, null)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("task").get("id").asText();
    }

    private ResponseEntity<String> claim(String companyId, String taskId, String agentId) {
        return rest.postForEntity("/api/v1/tasks/" + taskId + "/claim",
                new HttpEntity<>(headers(companyId, agentId)), String.class);
    }

    private ResponseEntity<String> putBudget(String companyId, String agentId, long capTokens) {
        Map<String, Object> body = agentId != null
                ? Map.of("agentId", agentId, "capTokens", capTokens)
                : Map.of("capTokens", capTokens);
        return rest.exchange("/api/v1/companies/" + companyId + "/budget", HttpMethod.PUT,
                new HttpEntity<>(body, headers(companyId, null)), String.class);
    }

    private boolean rosterPaused(String companyId, String agentId) {
        JsonNode roster = parse(rest.exchange("/api/v1/companies/" + companyId + "/roster",
                HttpMethod.GET, new HttpEntity<>(headers(companyId, null)), String.class).getBody());
        for (JsonNode agent : roster) {
            if (agent.get("id").asText().equals(agentId)) {
                return agent.get("paused").asBoolean();
            }
        }
        throw new AssertionError("Agent " + agentId + " not in roster");
    }

    private String currentPeriod() {
        return YearMonth.now().toString();
    }

    // ── agent-scope cap: claim refused, agent auto-paused, resumes after raise+unpause ──

    @Test
    void agentBudgetExhausted_blocksClaimAndAutoPauses() {
        String company = createCompany("m07-agent-cap");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Do the thing", "coding");

        assertThat(putBudget(company, agentId, 10).getStatusCode().value()).isEqualTo(200);
        jdbc.update("UPDATE budgets SET spent_tokens = 20 WHERE company_id = ?::uuid "
                + "AND agent_id = ?::uuid AND period = ?", company, agentId, currentPeriod());

        assertThat(claim(company, taskId, agentId).getStatusCode().value()).isEqualTo(409);
        assertThat(rosterPaused(company, agentId)).as("auto-paused on cap breach").isTrue();

        List<Map<String, Object>> exceeded = jdbc.queryForList(
                "SELECT payload FROM outbox_events WHERE event_type = 'budget.exceeded' "
                        + "AND payload->>'agentId' = ?", agentId);
        assertThat(exceeded).hasSize(1);
        assertThat(parse(exceeded.get(0).get("payload").toString()).get("period").asText())
                .isEqualTo(currentPeriod());

        // raise the cap, unpause — claim now succeeds
        assertThat(putBudget(company, agentId, 1000).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> unpause = rest.exchange("/api/v1/agents/" + agentId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("paused", false), headers(company, null)), String.class);
        assertThat(unpause.getStatusCode().value()).isEqualTo(200);
        assertThat(claim(company, taskId, agentId).getStatusCode().value()).isEqualTo(200);
    }

    // ── company-wide cap: claim refused but no single agent is arbitrarily paused ──

    @Test
    void companyWideBudgetExhausted_blocksClaimWithoutPausingAnyAgent() {
        String company = createCompany("m07-company-cap");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Do the thing", "coding");

        assertThat(putBudget(company, null, 10).getStatusCode().value()).isEqualTo(200);
        jdbc.update("UPDATE budgets SET spent_tokens = 20 WHERE company_id = ?::uuid "
                + "AND agent_id IS NULL AND period = ?", company, currentPeriod());

        assertThat(claim(company, taskId, agentId).getStatusCode().value()).isEqualTo(409);
        assertThat(rosterPaused(company, agentId))
                .as("company-wide breach doesn't arbitrarily pause one agent").isFalse();

        List<Map<String, Object>> exceeded = jdbc.queryForList(
                "SELECT payload FROM outbox_events WHERE event_type = 'budget.exceeded' "
                        + "AND company_id = ?::uuid", company);
        assertThat(exceeded).hasSize(1);
        assertThat(parse(exceeded.get(0).get("payload").toString()).has("agentId")).isFalse();
    }

    // ── soft alert: fires exactly once per period, dedup via alerted_at ─────

    @Test
    void thresholdEventFiresExactlyOnceWhenCrossingAlertPct() {
        String company = createCompany("m07-threshold");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Spend some tokens", "coding");

        assertThat(putBudget(company, agentId, 100).getStatusCode().value()).isEqualTo(200);

        UUID companyId = UUID.fromString(company);
        UUID agentUuid = UUID.fromString(agentId);
        UUID taskUuid = UUID.fromString(taskId);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // 85/100 crosses the default 80% alert_pct — first call fires the event.
        tx.executeWithoutResult(status -> usageRecorder.record(companyId, agentUuid, taskUuid, 1,
                "anthropic", "claude-sonnet-5", 50, 35, 10L));
        // still over threshold, but already alerted this period — must be a no-op.
        tx.executeWithoutResult(status -> usageRecorder.record(companyId, agentUuid, taskUuid, 2,
                "anthropic", "claude-sonnet-5", 10, 5, 5L));

        Integer thresholdEvents = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'budget.threshold' "
                        + "AND payload->>'agentId' = ?", Integer.class, agentId);
        assertThat(thresholdEvents).isEqualTo(1);

        Map<String, Object> budgetRow = jdbc.queryForMap(
                "SELECT spent_tokens, alerted_at FROM budgets WHERE company_id = ?::uuid "
                        + "AND agent_id = ?::uuid", company, agentId);
        assertThat(((Number) budgetRow.get("spent_tokens")).longValue()).isEqualTo(100L);
        assertThat(budgetRow.get("alerted_at")).isNotNull();
    }

    // ── a scope with no budget row is uncapped ───────────────────────────────

    @Test
    void noBudgetRowMeansUncapped() {
        String company = createCompany("m07-uncapped");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Free work", "coding");

        assertThat(claim(company, taskId, agentId).getStatusCode().value()).isEqualTo(200);
    }
}
