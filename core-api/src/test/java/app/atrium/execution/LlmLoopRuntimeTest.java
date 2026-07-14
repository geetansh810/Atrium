package app.atrium.execution;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.registry.runtime.AgentHandle;
import app.atrium.routing.WorkBroker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M0.5b Done-when (07's M0.5 verbatim): "reverse a string" produces a real
 * artifact from a real LLM call; a redelivered task doesn't double-record —
 * test forces a requeue mid-work. WireMock stands in for Anthropic so the
 * full Spring-wired LlmRouter/AnthropicClient path runs unmodified.
 */
class LlmLoopRuntimeTest extends IntegrationTestBase {

    static WireMockServer wiremock;

    @DynamicPropertySource
    static void anthropicOverrides(DynamicPropertyRegistry registry) {
        wiremock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wiremock.start();
        registry.add("atrium.llm.anthropic.base-url", wiremock::baseUrl);
        registry.add("atrium.llm.anthropic.api-key", () -> "sk-ant-test");
    }

    @AfterAll
    static void stopWireMock() {
        wiremock.stop();
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LlmLoopRuntime runtime;

    @Autowired
    WorkBroker workBroker;

    @Autowired
    UsageRecorder usageRecorder;

    @Autowired
    PlatformTransactionManager txManager;

    // ── helpers (same shapes as ClaimApiTest/RoutingApiTest) ─────────────────

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

    /** Hires via the API (which auto-starts the real loop), then stops that loop
     *  immediately so the test drives runOnce() deterministically — no race
     *  between the background poll thread and the explicit calls below. */
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

    private JsonNode getTask(String companyId, String taskId) {
        return parse(rest.exchange("/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers(companyId)), String.class).getBody()).get("task");
    }

    private void stubAnthropicSuccess(String responseText) {
        wiremock.stubFor(post(urlEqualTo("/v1/messages")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"msg_%s","type":"message","role":"assistant","model":"claude-sonnet-5",
                                 "content":[{"type":"text","text":"%s"}],
                                 "stop_reason":"end_turn","stop_sequence":null,
                                 "usage":{"input_tokens":42,"output_tokens":17}}
                                """.formatted(UUID.randomUUID(), responseText))));
    }

    private void awaitStatus(String companyId, String taskId, String wanted, int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = getTask(companyId, taskId).get("status").asText();
            if (wanted.equals(last)) return;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
        throw new AssertionError("Task " + taskId + " never became '" + wanted
                + "' within " + maxSeconds + "s (last: '" + last + "')");
    }

    // ── Done-when: real artifact from "reverse a string" ────────────────────

    @Test
    void realArtifactFromReverseAStringTask() {
        String company = createCompany("m05b");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Write a function that reverses a string", "coding");
        stubAnthropicSuccess("def reverse_string(s):\\n    return s[::-1]");

        runtime.runOnce(UUID.fromString(company), UUID.fromString(agentId));

        JsonNode detail = parse(rest.exchange("/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers(company)), String.class).getBody());
        assertThat(detail.get("task").get("status").asText()).isEqualTo("pending_review");
        assertThat(detail.get("task").get("progress").asInt()).isEqualTo(100);
        assertThat(detail.get("latestArtifact")).isNotNull();
        assertThat(detail.get("latestArtifact").get("kind").asText()).isEqualTo("text");
        assertThat(detail.get("latestArtifact").get("content").asText())
                .contains("def reverse_string");

        // usage recorded once, with the taskId:attempt idempotency key, real token counts
        List<Map<String, Object>> usage = jdbc.queryForList(
                "SELECT tokens_in, tokens_out, idempotency_key, cost_micro_usd, provider, model "
                        + "FROM usage_records WHERE task_id = ?::uuid", taskId);
        assertThat(usage).hasSize(1);
        assertThat(usage.get(0).get("tokens_in")).isEqualTo(42L);
        assertThat(usage.get(0).get("tokens_out")).isEqualTo(17L);
        assertThat(usage.get(0).get("idempotency_key")).isEqualTo(taskId + ":1");
        assertThat(usage.get(0).get("provider")).isEqualTo("anthropic");
        assertThat((Number) usage.get(0).get("cost_micro_usd")).isNotNull();

        // audit chain: created, claimed, progress (claimed->in_progress), completed
        List<String> eventTypes = jdbc.queryForList(
                "SELECT event_type FROM task_events WHERE task_id = ?::uuid ORDER BY created_at",
                String.class, taskId);
        assertThat(eventTypes).containsExactly("created", "claimed", "progress", "completed");
    }

    // ── Done-when: redelivered task doesn't double-record (forced requeue mid-work) ──

    @Test
    void redeliveredTaskDoesNotDoubleRecord() {
        String company = createCompany("m05b-redeliver");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Long-running work", "coding");
        stubAnthropicSuccess("done");

        UUID companyId = UUID.fromString(company);
        UUID agentUuid = UUID.fromString(agentId);
        UUID taskUuid = UUID.fromString(taskId);

        // Simulate: claim (attempt=1), step 5 (usage) already ran, then the
        // worker dies before step 6/7 — the lease expires mid-work.
        workBroker.claim(companyId, taskUuid, agentUuid);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        boolean firstRecord = tx.execute(status -> usageRecorder.record(companyId, agentUuid, taskUuid,
                1, "anthropic", "claude-sonnet-5", 42, 17, 100L));
        assertThat(firstRecord).as("first record for attempt 1 succeeds").isTrue();

        // Force the lease expired and let the REAL scheduled reclaim job requeue it
        // (never call the job directly — exercise the actual scheduled path).
        jdbc.update("UPDATE tasks SET lease_expires_at = now() - interval '1 minute' "
                + "WHERE id = ?::uuid", taskId);
        awaitStatus(company, taskId, "queued", 30);
        assertThat(getTask(company, taskId).get("assignedAgentId").isNull()).isTrue();

        // Redelivery: attempt 1's usage call comes again (e.g. a retried step 5) —
        // the idempotency key already exists, so this must be a silent no-op.
        boolean redelivered = tx.execute(status -> usageRecorder.record(companyId, agentUuid, taskUuid,
                1, "anthropic", "claude-sonnet-5", 42, 17, 100L));
        assertThat(redelivered).as("redelivered attempt-1 call is a no-op").isFalse();

        Integer attempt1Rows = jdbc.queryForObject(
                "SELECT count(*) FROM usage_records WHERE idempotency_key = ?",
                Integer.class, taskId + ":1");
        assertThat(attempt1Rows).as("still exactly one row for the redelivered key").isEqualTo(1);

        // The task itself is reprocessed cleanly on re-claim: attempt=2, its own
        // usage row, no interference from the stale attempt-1 redelivery above.
        runtime.runOnce(companyId, agentUuid);
        awaitStatus(company, taskId, "pending_review", 10);
        assertThat(getTask(company, taskId).get("attempt").asInt()).isEqualTo(2);

        List<Map<String, Object>> allUsage = jdbc.queryForList(
                "SELECT idempotency_key FROM usage_records WHERE task_id = ?::uuid ORDER BY idempotency_key",
                taskId);
        assertThat(allUsage).extracting(row -> row.get("idempotency_key"))
                .containsExactly(taskId + ":1", taskId + ":2");
    }

    // ── M0.6 Done-when: a rejected task's next attempt prompt contains the feedback ──

    @Test
    void rejectedTaskFeedbackReachesTheNextAttemptPrompt() {
        String company = createCompany("m06-feedback");
        String agentId = hireAgentAndStopAutoLoop(company, "coding");
        String taskId = createTask(company, "Write a haiku generator", "coding");
        stubAnthropicSuccess("def haiku(): pass");

        runtime.runOnce(UUID.fromString(company), UUID.fromString(agentId));
        awaitStatus(company, taskId, "pending_review", 10);

        ResponseEntity<String> rejectResponse = rest.postForEntity(
                "/api/v1/tasks/" + taskId + "/reject",
                new HttpEntity<>(Map.of("feedback", "Add type hints to every function"),
                        headers(company)), String.class);
        assertThat(rejectResponse.getStatusCode().value()).as(rejectResponse.getBody()).isEqualTo(200);
        awaitStatus(company, taskId, "queued", 10);

        stubAnthropicSuccess("def haiku() -> str: return ''");
        runtime.runOnce(UUID.fromString(company), UUID.fromString(agentId));
        awaitStatus(company, taskId, "pending_review", 10);

        assertThat(getTask(company, taskId).get("attempt").asInt()).isEqualTo(2);
        // exactly one of the two calls (the retry) carried the feedback
        wiremock.verify(1, postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(containing("Add type hints to every function")));
    }

    // ── M-CTX1 Done-when: claimed event carries skill provenance; prompt has "## Your skills" ──

    @Test
    void claimedEventCarriesSkillProvenanceAndThePromptContainsTheSkillsSection() {
        // every "coder"-hired agent gets the same 2 seeded skills, so other tests in
        // this class produce matching request bodies too — reset the log so this
        // test's verify() only counts its own call.
        wiremock.resetRequests();
        String company = createCompany("m-ctx1");
        String agentId = hireAgentAndStopAutoLoop(company, "coding"); // "coder" template -> 2 seeded skills
        String taskId = createTask(company, "Write a function that reverses a string", "coding");
        stubAnthropicSuccess("def reverse_string(s):\\n    return s[::-1]");

        runtime.runOnce(UUID.fromString(company), UUID.fromString(agentId));
        awaitStatus(company, taskId, "pending_review", 10);

        // the SAME "claimed" task_event carries the skill ids that were assembled into the prompt
        List<String> provenanceIds = jdbc.queryForList(
                "SELECT jsonb_array_elements_text(payload->'contextProvenance') FROM task_events "
                        + "WHERE task_id = ?::uuid AND event_type = 'claimed'", String.class, taskId);
        assertThat(provenanceIds).hasSize(2);

        // and the actual LLM request carried the "## Your skills" section built from those skills
        wiremock.verify(1, postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(containing("## Your skills"))
                .withRequestBody(containing("Code review checklist")));
    }
}
