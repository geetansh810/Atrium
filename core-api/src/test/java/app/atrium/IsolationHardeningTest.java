package app.atrium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import app.atrium.agentmind.EmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * M3.2's Done-when: for every 04 endpoint, a request authenticated as company
 * B against company A's resources 404s (indistinguishable from absent — the
 * app's own established convention, e.g. {@code CompanyService.get}'s
 * javadoc), and Redis events never cross company channels.
 *
 * <p>This is a second, database-enforced layer (Postgres RLS, 08 §Security
 * rule 6) behind the application-level {@code requireTenantMatch}/{@code
 * findByIdAndCompanyId} checks every module has carried since M0.1 — the
 * suite below exercises the HTTP surface exactly like a real attacker would,
 * so it fails the same way whether the app-level check OR the RLS policy is
 * what's actually stopping the read. Session-scoped instructions for the
 * "plant a bypass, confirm the suite catches it" verification live in the
 * M3.2 session notes (CLAUDE.md) — not committed here, since a real bypass
 * has no place staying in the tree.
 */
class IsolationHardeningTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @BeforeEach
    void stubEmbeddings() {
        when(embeddingClient.isReady()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(new float[1536]);
        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<?> texts = inv.getArgument(0);
            return texts.stream().map(t -> new float[1536]).toList();
        });
    }

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

    private ResponseEntity<String> post(String path, Object body, String asCompanyId) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(asCompanyId)), String.class);
    }

    private ResponseEntity<String> get(String path, String asCompanyId) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(asCompanyId)), String.class);
    }

    private ResponseEntity<String> put(String path, Object body, String asCompanyId) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(asCompanyId)), String.class);
    }

    private ResponseEntity<String> delete(String path, String asCompanyId) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(asCompanyId)), String.class);
    }

    // ── one shared fixture: everything company A owns, company B never should see ──

    private String companyA;
    private String companyB;
    private String agentA;
    private String roleDefA;
    private String taskA;
    private String skillA;
    private String knowledgeDocA;
    private String memoryA;
    private String channelA;

    private void seedCompanyAResources() {
        companyA = createCompany("iso-a");
        companyB = createCompany("iso-b");

        ResponseEntity<String> agentResp = post("/api/v1/companies/" + companyA + "/agents",
                Map.of("name", "A's Agent", "roleTemplateKey", "coder", "roleTitle", "Engineer",
                        "skillTags", List.of("coding"), "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"),
                companyA);
        assertThat(agentResp.getStatusCode().value()).as(agentResp.getBody()).isEqualTo(201);
        agentA = parse(agentResp.getBody()).get("id").asText();

        ResponseEntity<String> roleResp = post("/api/v1/role-definitions",
                Map.of("key", "iso-role", "title", "Iso Role", "systemPrompt", "You are a test role.",
                        "outputContract", "text"),
                companyA);
        assertThat(roleResp.getStatusCode().value()).as(roleResp.getBody()).isEqualTo(201);
        roleDefA = parse(roleResp.getBody()).get("id").asText();

        ResponseEntity<String> taskResp = post("/api/v1/companies/" + companyA + "/tasks",
                Map.of("title", "A's task", "requiredSkill", "coding"), companyA);
        assertThat(taskResp.getStatusCode().value()).as(taskResp.getBody()).isEqualTo(201);
        taskA = parse(taskResp.getBody()).get("task").get("id").asText();

        ResponseEntity<String> skillResp = post("/api/v1/companies/" + companyA + "/skills",
                Map.of("key", "iso-skill", "name", "Iso Skill", "description", "d", "bodyMd", "body",
                        "kind", "reference", "tags", List.of()),
                companyA);
        assertThat(skillResp.getStatusCode().value()).as(skillResp.getBody()).isEqualTo(201);
        skillA = parse(skillResp.getBody()).get("id").asText();

        ResponseEntity<String> knowledgeResp = post("/api/v1/companies/" + companyA + "/knowledge",
                Map.of("title", "A's doc", "content", "Secret A content"), companyA);
        assertThat(knowledgeResp.getStatusCode().value()).as(knowledgeResp.getBody()).isEqualTo(201);
        knowledgeDocA = parse(knowledgeResp.getBody()).get("id").asText();

        ResponseEntity<String> memoryResp = post("/api/v1/companies/" + companyA + "/memories",
                Map.of("scope", "company", "kind", "fact", "content", "A's secret fact"), companyA);
        assertThat(memoryResp.getStatusCode().value()).as(memoryResp.getBody()).isEqualTo(201);
        memoryA = parse(memoryResp.getBody()).get("id").asText();

        ResponseEntity<String> channelResp = post("/api/v1/companies/" + companyA + "/channels",
                Map.of("name", "a-secret-channel"), companyA);
        assertThat(channelResp.getStatusCode().value()).as(channelResp.getBody()).isEqualTo(201);
        channelA = parse(channelResp.getBody()).get("id").asText();

        assertThat(put("/api/v1/companies/" + companyA + "/budget",
                Map.of("period", "2026-01", "capTokens", 1_000_000), companyA)
                .getStatusCode().value()).isEqualTo(200);
    }

    /**
     * Every endpoint below is called with company B's own valid bearer token
     * against a resource id that belongs to company A — every one of these
     * must 404 (never 200, never a 500, never real data). Grouped by module
     * to match 04's own contract structure.
     */
    @Test
    void authenticatedAsBEveryEndpointAgainstAsResourcesReturns404() {
        seedCompanyAResources();

        // ── registry: companies, agents, role-definitions ──────────────────
        assertNotFound(get("/api/v1/companies/" + companyA, companyB));
        assertNotFound(get("/api/v1/companies/" + companyA + "/roster", companyB));
        assertNotFound(post("/api/v1/companies/" + companyA + "/agents",
                Map.of("name", "x", "roleTemplateKey", "coder", "roleTitle", "t",
                        "skillTags", List.of("coding"), "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"),
                companyB));
        assertNotFound(rest.exchange("/api/v1/agents/" + agentA, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("paused", true), headers(companyB)), String.class));
        assertNotFound(get("/api/v1/agents/" + agentA + "/profile", companyB));

        // ── routing: tasks, escalations, worker gateway ────────────────────
        assertNotFound(get("/api/v1/companies/" + companyA + "/tasks", companyB));
        assertNotFound(get("/api/v1/companies/" + companyA + "/escalations", companyB));
        assertNotFound(get("/api/v1/tasks/" + taskA, companyB));
        assertNotFound(get("/api/v1/tasks/" + taskA + "/events", companyB));
        assertNotFound(get("/api/v1/tasks/" + taskA + "/flow", companyB));
        assertNotFound(post("/api/v1/tasks/" + taskA + "/approve", null, companyB));
        assertNotFound(post("/api/v1/tasks/" + taskA + "/reject", Map.of("feedback", "no"), companyB));

        // ── accountability: budget, analytics ──────────────────────────────
        assertNotFound(get("/api/v1/companies/" + companyA + "/budget", companyB));
        assertNotFound(put("/api/v1/companies/" + companyA + "/budget",
                Map.of("period", "2026-01", "capTokens", 1), companyB));
        assertNotFound(get("/api/v1/companies/" + companyA + "/analytics/summary", companyB));
        assertNotFound(get("/api/v1/companies/" + companyA + "/analytics/agent-performance", companyB));

        // ── agentmind: skills, knowledge, memories, mind ───────────────────
        assertNotFound(get("/api/v1/companies/" + companyA + "/skills", companyB));
        assertNotFound(get("/api/v1/skills/" + skillA, companyB));
        assertNotFound(post("/api/v1/skills/" + skillA + "/versions",
                Map.of("name", "n", "description", "d", "bodyMd", "b"), companyB));
        assertNotFound(post("/api/v1/agents/" + agentA + "/skills",
                Map.of("skillId", skillA), companyB));
        assertNotFound(delete("/api/v1/agents/" + agentA + "/skills/" + skillA, companyB));
        assertNotFound(post("/api/v1/role-definitions/" + roleDefA + "/skills",
                Map.of("skillId", skillA), companyB));
        assertNotFound(get("/api/v1/agents/" + agentA + "/mind", companyB));

        assertNotFound(get("/api/v1/companies/" + companyA + "/knowledge", companyB));
        assertNotFound(delete("/api/v1/knowledge/" + knowledgeDocA, companyB));
        assertNotFound(post("/api/v1/role-definitions/" + roleDefA + "/knowledge",
                Map.of("docId", knowledgeDocA), companyB));

        assertNotFound(get("/api/v1/companies/" + companyA + "/memories", companyB));
        assertNotFound(get("/api/v1/companies/" + companyA + "/memories/review-queue", companyB));
        assertNotFound(delete("/api/v1/memories/" + memoryA, companyB));
        assertNotFound(post("/api/v1/memories/" + memoryA + "/review",
                Map.of("action", "approve"), companyB));

        // ── communication: channels, messages, announcements ──────────────
        assertNotFound(get("/api/v1/companies/" + companyA + "/channels", companyB));
        assertNotFound(get("/api/v1/channels/" + channelA + "/messages", companyB));
        assertNotFound(post("/api/v1/channels/" + channelA + "/messages",
                Map.of("text", "leaked message"), companyB));
        assertNotFound(get("/api/v1/companies/" + companyA + "/announcements", companyB));
        assertNotFound(post("/api/v1/companies/" + companyA + "/announcements",
                Map.of("title", "t", "body", "b", "category", "company"), companyB));

        // ── the same requests, replayed as company A, all succeed — proves
        //    the 404s above are tenant isolation, not a broken endpoint ────
        assertThat(get("/api/v1/companies/" + companyA, companyA).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/tasks/" + taskA, companyA).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/skills/" + skillA, companyA).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/companies/" + companyA + "/memories", companyA).getStatusCode().value())
                .isEqualTo(200);
        assertThat(get("/api/v1/channels/" + channelA + "/messages", companyA).getStatusCode().value())
                .isEqualTo(200);
    }

    private void assertNotFound(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).as("expected 404, got body: " + response.getBody())
                .isEqualTo(404);
    }

    // ── the worker gateway is a separate auth axis (X-Agent-Id, not a JWT) —
    //    a company-B task must never be claimable by a company-A agent id ──

    @Test
    void workerGatewayNeverLetsAnAgentClaimAnotherCompanysTask() {
        seedCompanyAResources();

        HttpHeaders agentAHeaders = new HttpHeaders();
        agentAHeaders.setContentType(MediaType.APPLICATION_JSON);
        agentAHeaders.set("X-Agent-Id", agentA);
        ResponseEntity<String> claim = rest.exchange("/api/v1/tasks/" + taskA + "/claim", HttpMethod.POST,
                new HttpEntity<>(agentAHeaders), String.class);
        // agentA legitimately belongs to companyA and taskA is companyA's own —
        // this must succeed, proving the gateway isn't just broken outright.
        assertThat(claim.getStatusCode().value()).as(claim.getBody()).isEqualTo(200);

        // Now company B hires its own agent and that agent must NEVER be able
        // to claim one of company A's (other, still-open) tasks.
        ResponseEntity<String> secondTask = post("/api/v1/companies/" + companyA + "/tasks",
                Map.of("title", "A's second task", "requiredSkill", "coding"), companyA);
        String secondTaskId = parse(secondTask.getBody()).get("task").get("id").asText();

        ResponseEntity<String> bAgentResp = post("/api/v1/companies/" + companyB + "/agents",
                Map.of("name", "B's Agent", "roleTemplateKey", "coder", "roleTitle", "Engineer",
                        "skillTags", List.of("coding"), "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"),
                companyB);
        String bAgentId = parse(bAgentResp.getBody()).get("id").asText();

        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setContentType(MediaType.APPLICATION_JSON);
        agentHeaders.set("X-Agent-Id", bAgentId);
        ResponseEntity<String> crossClaim = rest.exchange("/api/v1/tasks/" + secondTaskId + "/claim",
                HttpMethod.POST, new HttpEntity<>(agentHeaders), String.class);
        assertThat(crossClaim.getStatusCode().value()).as(crossClaim.getBody()).isEqualTo(404);
    }

    // ── Redis events never cross company channels ──────────────────────────

    @Test
    void redisEventsNeverCrossCompanyChannels() throws Exception {
        companyA = createCompany("iso-redis-a");
        companyB = createCompany("iso-redis-b");

        BlockingQueue<String> onB = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener((message, pattern) -> onB.add(new String(message.getBody())),
                new ChannelTopic("atrium:events:" + companyB));
        container.afterPropertiesSet();
        container.start();
        try {
            // A real event for company A only — company_id namespaces the
            // Redis channel itself (Topics.task/budget/chat), so this is
            // structurally impossible to leak, not just empirically absent.
            post("/api/v1/companies/" + companyA + "/agents",
                    Map.of("name", "A's agent", "roleTemplateKey", "coder", "roleTitle", "Engineer",
                            "skillTags", List.of("coding"), "modelProvider", "anthropic",
                            "modelName", "claude-sonnet-5"),
                    companyA);
            post("/api/v1/companies/" + companyA + "/tasks",
                    Map.of("title", "A's isolated task", "requiredSkill", "coding"), companyA);

            String leaked = onB.poll(2, TimeUnit.SECONDS);
            assertThat(leaked).as("company B's Redis channel must never see company A's events").isNull();
        } finally {
            container.stop();
        }
    }
}
