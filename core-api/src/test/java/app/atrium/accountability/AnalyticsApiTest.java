package app.atrium.accountability;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
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

/**
 * M2.3 Done-when: "What did this agent cost last week?" answerable via
 * {@code /analytics/agent-performance}; KPI cards match a hand-checked query
 * (seeded directly into {@code agent_stats_daily}/{@code usage_records} here,
 * independent of {@link StatsRollupTest}'s own pipeline coverage).
 */
class AnalyticsApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    JdbcTemplate jdbc = adminJdbc();

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

    private String createTask(String companyId, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", "T", "requiredSkill", skill), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("task").get("id").asText();
    }

    private void seedStatsDay(String companyId, String agentId, String skill, String isoDay,
                              int completed, int approved, int rejected, long tokens, long costMicroUsd) {
        jdbc.update("""
                INSERT INTO agent_stats_daily
                    (company_id, agent_id, skill, day, tasks_completed, tasks_approved,
                     tasks_rejected, tokens_spent, cost_micro_usd)
                VALUES (?::uuid, ?::uuid, ?, ?::date, ?, ?, ?, ?, ?)
                """, companyId, agentId, skill, isoDay, completed, approved, rejected, tokens, costMicroUsd);
    }

    private void seedUsageRecord(String companyId, String agentId, String taskId,
                                 long tokensIn, long tokensOut, long costMicroUsd, String idempotencyKey) {
        jdbc.update("""
                INSERT INTO usage_records
                    (company_id, agent_id, task_id, provider, model, tokens_in, tokens_out,
                     cost_micro_usd, idempotency_key)
                VALUES (?::uuid, ?::uuid, ?::uuid, 'anthropic', 'claude-sonnet-5', ?, ?, ?, ?)
                """, companyId, agentId, taskId, tokensIn, tokensOut, costMicroUsd, idempotencyKey);
    }

    private ResponseEntity<String> get(String companyId, String path) {
        return rest.exchange("/api/v1/companies/" + companyId + path, HttpMethod.GET,
                new HttpEntity<>(headers(companyId)), String.class);
    }

    @Test
    void summaryReflectsTodayPlusAllTimeSuccessRate() {
        String company = createCompany("m23-summary");
        String agentId = hireAgent(company, "coding");
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String yesterday = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString();

        seedStatsDay(company, agentId, "coding", today, 3, 2, 1, 900, 300);
        seedStatsDay(company, agentId, "coding", yesterday, 5, 4, 1, 500, 200);

        ResponseEntity<String> response = get(company, "/analytics/summary");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = parse(response.getBody());
        assertThat(body.get("tasksCompletedToday").asLong()).isEqualTo(3);
        assertThat(body.get("tasksApprovedToday").asLong()).isEqualTo(2);
        assertThat(body.get("tasksRejectedToday").asLong()).isEqualTo(1);
        assertThat(body.get("tokensSpentToday").asLong()).isEqualTo(900);
        assertThat(body.get("costMicroUsdToday").asLong()).isEqualTo(300);
        // all-time: approved=6, rejected=2 -> 6/8 = 75.0%
        assertThat(body.get("successRateAllTime").asDouble()).isEqualTo(75.0);
    }

    @Test
    void tasks7dZeroFillsDaysWithNoActivity() {
        String company = createCompany("m23-7d");
        String agentId = hireAgent(company, "coding");
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();

        seedStatsDay(company, agentId, "coding", today, 4, 0, 0, 0, 0);

        ResponseEntity<String> response = get(company, "/analytics/tasks-7d");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = parse(response.getBody());
        assertThat(body).hasSize(7);
        assertThat(body.get(6).get("day").asText()).isEqualTo(today);
        assertThat(body.get(6).get("count").asLong()).isEqualTo(4);
        assertThat(body.get(0).get("count").asLong()).isEqualTo(0);
    }

    @Test
    void agentPerformanceAnswersWhatDidThisAgentCostLastWeek() {
        String company = createCompany("m23-perf");
        String agentId = hireAgent(company, "coding");
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String eightDaysAgo = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(8).toString();

        seedStatsDay(company, agentId, "coding", today, 2, 1, 1, 1000, 500);
        // outside the default 7-day window — must NOT be counted
        seedStatsDay(company, agentId, "coding", eightDaysAgo, 100, 100, 0, 999999, 999999);

        ResponseEntity<String> response = get(company, "/analytics/agent-performance");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = parse(response.getBody());
        assertThat(body).hasSize(1);
        JsonNode row = body.get(0);
        assertThat(row.get("agentId").asText()).isEqualTo(agentId);
        assertThat(row.get("tasksCompleted").asLong()).isEqualTo(2);
        assertThat(row.get("tokensSpent").asLong()).isEqualTo(1000);
        assertThat(row.get("costMicroUsd").asLong()).isEqualTo(500);
        assertThat(row.get("successRate").asDouble()).isEqualTo(50.0);
    }

    @Test
    void topSkillsComputesShareAcrossSkills() {
        String company = createCompany("m23-skills");
        String coder = hireAgent(company, "coding");
        String writer = hireAgent(company, "content");
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();

        seedStatsDay(company, coder, "coding", today, 3, 0, 0, 0, 0);
        seedStatsDay(company, writer, "content", today, 1, 0, 0, 0, 0);

        ResponseEntity<String> response = get(company, "/analytics/top-skills");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = parse(response.getBody());
        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("skill").asText()).isEqualTo("coding");
        assertThat(body.get(0).get("tasksCompleted").asLong()).isEqualTo(3);
        assertThat(body.get(0).get("sharePct").asDouble()).isEqualTo(75.0);
        assertThat(body.get(1).get("sharePct").asDouble()).isEqualTo(25.0);
    }

    @Test
    void costPerTaskQueriesUsageRecordsDirectlyForThePeriod() {
        String company = createCompany("m23-cost");
        String agentId = hireAgent(company, "coding");
        String cheapTask = createTask(company, "coding");
        String expensiveTask = createTask(company, "coding");

        seedUsageRecord(company, agentId, cheapTask, 100, 50, 30L, cheapTask + ":1");
        seedUsageRecord(company, agentId, expensiveTask, 900, 400, 500L, expensiveTask + ":1");

        String period = YearMonth.now(java.time.Clock.systemUTC()).toString();
        ResponseEntity<String> response = get(company, "/analytics/cost-per-task?period=" + period);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = parse(response.getBody());
        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("taskId").asText()).isEqualTo(expensiveTask);
        assertThat(body.get(0).get("costMicroUsd").asLong()).isEqualTo(500);
        assertThat(body.get(0).get("tokens").asLong()).isEqualTo(1300);
        assertThat(body.get(1).get("taskId").asText()).isEqualTo(cheapTask);
    }

    @Test
    void tenantIsolation_companyBSeesNothingOfCompanyA() {
        String companyA = createCompany("m23-tenant-a");
        String companyB = createCompany("m23-tenant-b");
        String agentA = hireAgent(companyA, "coding");
        seedStatsDay(companyA, agentA, "coding",
                java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(), 5, 5, 0, 100, 40);

        ResponseEntity<String> summaryB = get(companyB, "/analytics/summary");
        assertThat(parse(summaryB.getBody()).get("tasksCompletedToday").asLong()).isEqualTo(0);

        ResponseEntity<String> perfB = get(companyB, "/analytics/agent-performance");
        assertThat(parse(perfB.getBody())).isEmpty();

        // cross-tenant path param mismatch -> 404, same pattern as BudgetController
        ResponseEntity<String> crossTenant = rest.exchange(
                "/api/v1/companies/" + companyA + "/analytics/summary", HttpMethod.GET,
                new HttpEntity<>(headers(companyB)), String.class);
        assertThat(crossTenant.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void invalidQueryParamsReturn400() {
        String company = createCompany("m23-validation");
        assertThat(get(company, "/analytics/agent-performance?days=0").getStatusCode().value()).isEqualTo(400);
        assertThat(get(company, "/analytics/agent-performance?days=91").getStatusCode().value()).isEqualTo(400);
        assertThat(get(company, "/analytics/cost-per-task?period=not-a-period").getStatusCode().value())
                .isEqualTo(400);
        assertThat(get(company, "/analytics/cost-per-task?limit=51").getStatusCode().value()).isEqualTo(400);
    }
}
