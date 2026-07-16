package app.atrium.agentmind;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.registry.runtime.AgentHandle;
import app.atrium.routing.TaskService;
import app.atrium.routing.WorkBroker;
import app.atrium.routing.domain.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * M-LN1 Done-when (17): reject a task with feedback → the agent gets an
 * auto-active lesson AND a pending company fact; approving the fact makes it
 * recall for OTHER agents; replaying the same event (cursor reset) creates
 * nothing new; a rejected memory never appears in prompts (the last part is
 * covered by {@code MemoryApiTest#rejectedMemoryNeverAppearsInRecall}, since
 * it needs no LLM setup — kept there rather than duplicated here).
 *
 * <p>WireMock stands in for Anthropic, same pattern as {@code
 * LlmLoopRuntimeTest}. {@link LearningPipeline#pollOnce} is called directly
 * (the base class's own {@code EventCursorWorkerTest} precedent) rather than
 * waiting on the real {@code @Scheduled} tick, which is disabled in the test
 * profile (see {@code application-test.yml}) specifically to keep every OTHER
 * shared-context test from triggering real extraction calls.
 */
class LearningPipelineTest extends IntegrationTestBase {

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
    LearningPipeline learningPipeline;

    @Autowired
    LlmLoopRuntime runtime;

    @Autowired
    WorkBroker workBroker;

    @Autowired
    TaskService taskService;

    @Autowired
    MemoryStore memoryStore;

    /** No real embeddings provider in this test — every embed() call returns the same non-zero vector. */
    @MockitoBean
    EmbeddingClient embeddingClient;

    /** Both tests here stub the same URL with no distinguishing request matcher — clear
     *  stale mappings from a previous test method so WireMock's tie-break rule for
     *  equal-priority stubs can never accidentally serve the wrong test's response. */
    @BeforeEach
    void resetWiremockStubs() {
        wiremock.resetMappings();
    }

    // ── helpers (same shapes as LlmLoopRuntimeTest) ─────────────────────────

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

    /** Hires via the API (which auto-starts the real llm_loop), then stops that loop
     *  immediately so this test's direct WorkBroker/TaskService calls don't race it —
     *  same precedent as LlmLoopRuntimeTest. */
    private String hireAgent(String companyId, String skill) {
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

    /** Sets this consumer's cursor to the outbox table's current max id, so pollOnce() in
     *  this test only ever sees events created from this point on — the Testcontainers
     *  Postgres is reused/shared across every test class in the suite. */
    private void resetLearningCursorToNow() {
        Long baseline = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox_events", Long.class);
        jdbc.update("""
                INSERT INTO event_consumers(consumer_name, last_event_id) VALUES (?, ?)
                ON CONFLICT (consumer_name) DO UPDATE SET last_event_id = EXCLUDED.last_event_id
                """, LearningPipeline.CONSUMER_NAME, baseline);
    }

    private void stubExtraction(String rawJsonContent) throws Exception {
        String escapedContent = json.writeValueAsString(rawJsonContent); // includes surrounding quotes
        wiremock.stubFor(post(urlEqualTo("/v1/messages")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"id":"msg_%s","type":"message","role":"assistant","model":"claude-sonnet-5",
                         "content":[{"type":"text","text":%s}],
                         "stop_reason":"end_turn","stop_sequence":null,
                         "usage":{"input_tokens":50,"output_tokens":30}}
                        """.formatted(UUID.randomUUID(), escapedContent))));
    }

    private Long latestOutboxEventId(String taskId, String eventType) {
        return jdbc.queryForObject(
                "SELECT id FROM outbox_events WHERE event_type = ? AND payload->>'taskId' = ? "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class, eventType, taskId);
    }

    // ── Done-when: reject → auto-active lesson + pending company fact → approve → recall for another agent ──

    @Test
    void rejectionProducesGovernedMemoriesAndApprovalMakesTheFactRecallForOtherAgents() throws Exception {
        float[] fixedVector = new float[1536];
        fixedVector[0] = 1f;
        when(embeddingClient.isReady()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(fixedVector);
        resetLearningCursorToNow();

        String company = createCompany("ln1-reject");
        String agentId = hireAgent(company, "coding");
        String taskId = createTask(company, "Write a landing page blurb", "coding");

        UUID companyId = UUID.fromString(company);
        UUID agentUuid = UUID.fromString(agentId);
        UUID taskUuid = UUID.fromString(taskId);
        workBroker.claim(companyId, taskUuid, agentUuid);
        taskService.progress(companyId, taskUuid, agentUuid, null, null, null);
        taskService.complete(companyId, taskUuid, agentUuid, "text", "Welcome to our site.");

        stubExtraction("""
                {"items":[
                  {"content":"Avoid passive voice in copy","kind":"lesson","scope":"agent"},
                  {"content":"our brand name is X-Corp","kind":"fact","scope":"company"}
                ]}""");

        Task rejected = taskService.reject(companyId, taskUuid,
                "never use passive voice; our brand name is X-Corp");
        assertThat(rejected.getStatus()).isEqualTo("queued");

        learningPipeline.pollOnce(20);

        // agent-scope lesson landed active, no human needed
        List<Map<String, Object>> lessons = jdbc.queryForList("""
                SELECT status, content FROM memories
                WHERE company_id = ?::uuid AND agent_id = ?::uuid AND scope = 'agent' AND kind = 'lesson'
                """, company, agentId);
        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).get("status")).isEqualTo("active");
        assertThat((String) lessons.get(0).get("content")).contains("passive voice");

        // company-scope fact is pending review — never auto-active, even though it came
        // from the same extraction call as the auto-active lesson above
        List<Map<String, Object>> facts = jdbc.queryForList("""
                SELECT id, status, content FROM memories
                WHERE company_id = ?::uuid AND scope = 'company' AND kind = 'fact'
                """, company);
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).get("status")).isEqualTo("pending_review");
        assertThat((String) facts.get(0).get("content")).contains("X-Corp");
        String factId = facts.get(0).get("id").toString();

        // usage metered like any LLM call, keyed learn:{eventId} (14 §5 "learning is payroll too")
        Long rejectedEventId = latestOutboxEventId(taskId, "task.rejected");
        Integer usageRows = jdbc.queryForObject(
                "SELECT count(*) FROM usage_records WHERE idempotency_key = ?",
                Integer.class, "learn:" + rejectedEventId);
        assertThat(usageRows).isEqualTo(1);

        // approving the fact makes it recall for a DIFFERENT agent (company scope, no agent needed)
        ResponseEntity<String> reviewed = rest.postForEntity("/api/v1/memories/" + factId + "/review",
                new HttpEntity<>(Map.of("action", "approve"), headers(company)), String.class);
        assertThat(reviewed.getStatusCode().value()).as(reviewed.getBody()).isEqualTo(200);
        assertThat(parse(reviewed.getBody()).get("status").asText()).isEqualTo("active");

        UUID otherAgentId = UUID.randomUUID();
        List<MemoryHit> recalledForOtherAgent = memoryStore.recall(
                new RecallQuery(companyId, otherAgentId, "some-other-role", "brand name", 10, null));
        assertThat(recalledForOtherAgent).extracting(h -> h.memory().id())
                .contains(UUID.fromString(factId));

        // ── idempotency: replaying the SAME event (cursor rewound) creates nothing new ──
        jdbc.update("UPDATE event_consumers SET last_event_id = ? WHERE consumer_name = ?",
                rejectedEventId - 1, LearningPipeline.CONSUMER_NAME);
        learningPipeline.pollOnce(20);

        Integer lessonCountAfterReplay = jdbc.queryForObject("""
                SELECT count(*) FROM memories
                WHERE company_id = ?::uuid AND agent_id = ?::uuid AND scope = 'agent' AND kind = 'lesson'
                """, Integer.class, company, agentId);
        assertThat(lessonCountAfterReplay).as("no duplicate lesson row on replay").isEqualTo(1);

        Integer factCountAfterReplay = jdbc.queryForObject(
                "SELECT count(*) FROM memories WHERE company_id = ?::uuid AND scope = 'company' AND kind = 'fact'",
                Integer.class, company);
        assertThat(factCountAfterReplay).as("no duplicate fact row on replay").isEqualTo(1);

        Integer usageRowsAfterReplay = jdbc.queryForObject(
                "SELECT count(*) FROM usage_records WHERE idempotency_key = ?",
                Integer.class, "learn:" + rejectedEventId);
        assertThat(usageRowsAfterReplay).as("usage_records stays deduped on replay").isEqualTo(1);

        // the duplicate probe bumped importance on the dedup hit instead of silently doing nothing
        Integer lessonImportance = jdbc.queryForObject("""
                SELECT importance FROM memories
                WHERE company_id = ?::uuid AND agent_id = ?::uuid AND scope = 'agent' AND kind = 'lesson'
                """, Integer.class, company, agentId);
        assertThat(lessonImportance).isEqualTo(2);
    }

    // ── task.completed → episodic summary, agent scope, auto-active ─────────

    @Test
    void completedTaskProducesAnAutoActiveEpisodicSummary() throws Exception {
        float[] fixedVector = new float[1536];
        fixedVector[0] = 1f;
        when(embeddingClient.isReady()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(fixedVector);
        resetLearningCursorToNow();

        String company = createCompany("ln1-completed");
        String agentId = hireAgent(company, "coding");
        String taskId = createTask(company, "Summarize Q2 sales", "coding");

        UUID companyId = UUID.fromString(company);
        UUID agentUuid = UUID.fromString(agentId);
        UUID taskUuid = UUID.fromString(taskId);
        workBroker.claim(companyId, taskUuid, agentUuid);
        taskService.progress(companyId, taskUuid, agentUuid, null, null, null);

        stubExtraction("""
                {"summary":"Wrote a Q2 sales summary covering revenue and top accounts."}""");
        taskService.complete(companyId, taskUuid, agentUuid, "text", "Q2 revenue rose 12%...");

        learningPipeline.pollOnce(20);

        List<Map<String, Object>> summaries = jdbc.queryForList("""
                SELECT status, content FROM memories
                WHERE company_id = ?::uuid AND agent_id = ?::uuid AND scope = 'agent' AND kind = 'summary'
                """, company, agentId);
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).get("status")).isEqualTo("active");
        assertThat((String) summaries.get(0).get("content")).contains("Q2 sales summary");

        // sanity: the extraction call really carried the task's own title/description
        wiremock.verify(1, postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(containing("Summarize Q2 sales")));
    }

    // ── M-LN2-fix: a missing embeddings key must not block extraction ───────

    /**
     * The real gap M-LN2's own session hit: with no embeddings provider configured,
     * {@code LearningPipeline} used to skip every event outright (an {@code isReady()}
     * gate in {@code handle()}), so no memory was ever written and the review queue
     * stayed empty forever. Fixed by making {@code MemoryStore#ingest} degrade to a
     * NULL-embedding row instead (matching {@code recall}/{@code findDuplicate}'s
     * existing posture) and removing the gate — this test proves extraction now runs
     * end-to-end without an embeddings key: the governed writes still land, still get
     * metered, they just aren't semantically recallable until a real key is set.
     */
    @Test
    void extractionStillWritesGovernedMemoriesWhenEmbeddingsAreNotConfigured() throws Exception {
        when(embeddingClient.isReady()).thenReturn(false);
        resetLearningCursorToNow();

        String company = createCompany("ln2fix-noembed");
        String agentId = hireAgent(company, "coding");
        String taskId = createTask(company, "Write a landing page blurb", "coding");

        UUID companyId = UUID.fromString(company);
        UUID agentUuid = UUID.fromString(agentId);
        UUID taskUuid = UUID.fromString(taskId);
        workBroker.claim(companyId, taskUuid, agentUuid);
        taskService.progress(companyId, taskUuid, agentUuid, null, null, null);
        taskService.complete(companyId, taskUuid, agentUuid, "text", "Welcome to our site.");

        stubExtraction("""
                {"items":[
                  {"content":"Avoid passive voice in copy","kind":"lesson","scope":"agent"},
                  {"content":"our brand name is X-Corp","kind":"fact","scope":"company"}
                ]}""");

        taskService.reject(companyId, taskUuid, "never use passive voice; our brand name is X-Corp");

        learningPipeline.pollOnce(20);

        // extraction ran and governed writes landed exactly as they would with a real key —
        // the missing embeddings key never reached the extraction LLM call at all
        List<Map<String, Object>> lessons = jdbc.queryForList("""
                SELECT status, embedding FROM memories
                WHERE company_id = ?::uuid AND agent_id = ?::uuid AND scope = 'agent' AND kind = 'lesson'
                """, company, agentId);
        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).get("status")).isEqualTo("active");
        assertThat(lessons.get(0).get("embedding")).as("lands with no vector, not rejected").isNull();

        List<Map<String, Object>> facts = jdbc.queryForList("""
                SELECT status, embedding FROM memories WHERE company_id = ?::uuid AND scope = 'company' AND kind = 'fact'
                """, company);
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).get("status")).isEqualTo("pending_review");
        assertThat(facts.get(0).get("embedding")).isNull();

        // still metered like any other extraction call
        Long rejectedEventId = latestOutboxEventId(taskId, "task.rejected");
        Integer usageRows = jdbc.queryForObject(
                "SELECT count(*) FROM usage_records WHERE idempotency_key = ?",
                Integer.class, "learn:" + rejectedEventId);
        assertThat(usageRows).isEqualTo(1);

        // embed() itself was never called — isReady()==false short-circuits before any attempt
        verify(embeddingClient, never()).embed(anyString());

        // honest limitation, not silently faked: even once embeddings come back online,
        // a row written with a NULL vector still can't be found by recall() — the SQL's
        // own "embedding IS NOT NULL" filter excludes it, independent of the isReady() gate
        float[] fixedVector = new float[1536];
        fixedVector[0] = 1f;
        when(embeddingClient.isReady()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(fixedVector);
        List<MemoryHit> recalled = memoryStore.recall(
                new RecallQuery(companyId, agentUuid, null, "passive voice", 10, null));
        assertThat(recalled).extracting(h -> h.memory().id())
                .as("not recallable until a real embeddings key backfills a vector for this row")
                .isEmpty();
    }
}
