package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import app.atrium.IntegrationTestBase;
import app.atrium.common.TenantContext;
import app.atrium.agentmind.api.KnowledgeDtos.IngestKnowledgeRequest;
import app.atrium.agentmind.domain.AgentSkill;
import app.atrium.agentmind.domain.AgentSkillRepository;
import app.atrium.agentmind.domain.Skill;
import app.atrium.agentmind.domain.SkillRepository;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.AgentRepository;
import app.atrium.routing.domain.Task;
import app.atrium.routing.domain.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

/**
 * M-CTX1 unit-level Done-when: fits-all, item-granular truncation, name+description
 * fallback, deterministic assembly, role-then-proficiency ordering, trust-level
 * filtering, and the configured/hard-cap budget resolution. The claimed-event
 * provenance + prompt-fixture Done-when live in {@link
 * app.atrium.execution.LlmLoopRuntimeTest}, since that's where the claim ->
 * assemble -> prompt pipeline is actually wired together.
 */
class SkillContextAssemblerTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    ContextAssembler contextAssembler;

    @Autowired
    AgentRepository agentRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    SkillRepository skillRepository;

    @Autowired
    AgentSkillRepository agentSkillRepository;

    @Autowired
    MemoryStore memoryStore;

    @Autowired
    KnowledgeService knowledgeService;

    @Autowired
    PlatformTransactionManager transactionManager;

    /**
     * M3.2: repositories injected directly into a test (no wrapping
     * {@code @Transactional} service method, unlike everywhere in real app
     * code) don't reliably open a Spring-managed transaction on their own —
     * confirmed by instrumentation: {@code SimpleJpaRepository}'s own
     * class-level {@code @Transactional(readOnly=true)} did not fire {@code
     * TenantAwareJpaTransactionManager#doBegin} for a bare repository call
     * from this test's own thread, so the RLS session GUC (08 §Security rule
     * 6) was never set and every such read/write silently saw zero rows. An
     * explicit {@link TransactionTemplate} guarantees a real transaction (and
     * therefore a real {@code doBegin} call) regardless of that gap. Bind the
     * tenant with {@link TenantContext#callAsSystem} OUTSIDE the transaction
     * (it must be set before the transaction begins, not during it).
     */
    private <T> T inTx(String companyId, Supplier<T> work) {
        return TenantContext.callAsSystem(UUID.fromString(companyId),
                () -> new TransactionTemplate(transactionManager).execute(status -> work.get()));
    }

    /**
     * Real HTTP calls are never made in this class — every embed() call
     * returns a deterministic basis vector so cosine similarity is exactly
     * controlled per test rather than depending on a live embeddings provider.
     * Gives this test class its own Spring context (a mocked bean = a
     * different context-cache key), same precedent as {@code
     * OutboxRelayTest}'s {@code @MockitoSpyBean}.
     */
    @MockitoBean
    EmbeddingClient embeddingClient;

    private static float[] basisVector(int dimIndex) {
        float[] v = new float[1536];
        v[dimIndex] = 1f;
        return v;
    }

    /** Same vector everywhere by default -> cosine similarity 1.0 for any content vs. any query. */
    private static final float[] SIMILAR_VECTOR = basisVector(0);
    /** Orthogonal to SIMILAR_VECTOR -> cosine similarity 0.0. */
    private static final float[] DISSIMILAR_VECTOR = basisVector(1);

    @BeforeEach
    void stubEmbeddings() {
        when(embeddingClient.isReady()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(SIMILAR_VECTOR);
        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<?> texts = inv.getArgument(0);
            return texts.stream().map(t -> SIMILAR_VECTOR).toList();
        });
    }

    private UUID createRole(String companyId, String key) {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/role-definitions",
                new HttpEntity<>(Map.of("key", key, "title", key, "systemPrompt", "You are a " + key + ".",
                        "outputContract", "markdown"), headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return UUID.fromString(parse(response.getBody()).get("id").asText());
    }

    // M3.2: every direct repo/service-bean call in this file (as opposed to a
    // real rest.* HTTP call, which already binds TenantContext via
    // TenantContextFilter) has to bind its own tenant for RLS (08 §Security
    // rule 6) — this class deliberately never goes through HTTP for the
    // assemble/seed/direct-repo paths it's testing.
    private UUID ingestKnowledgeDoc(String companyId, String title, String content) {
        return TenantContext.callAsSystem(UUID.fromString(companyId), () -> knowledgeService.ingest(
                UUID.fromString(companyId), new IngestKnowledgeRequest(title, content, null)).getId());
    }

    private void attachKnowledgeToRole(String companyId, UUID roleDefinitionId, UUID docId) {
        TenantContext.runAsSystem(UUID.fromString(companyId),
                () -> knowledgeService.attachToRole(UUID.fromString(companyId), roleDefinitionId, docId));
    }

    private String hireAgentWithCustomRole(String companyId, UUID roleDefinitionId, String skill) {
        Map<String, Object> body = Map.of(
                "name", "Agent-" + UUID.randomUUID().toString().substring(0, 6),
                "roleDefinitionId", roleDefinitionId.toString(),
                "roleTitle", "Title",
                "skillTags", List.of(skill),
                "modelProvider", "anthropic",
                "modelName", "claude-sonnet-5");
        ResponseEntity<String> response = rest.postForEntity("/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(body, headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private UUID seedMemory(String companyId, String scope, String agentId, String roleKey,
                           String kind, String content, String status) {
        ObjectNode provenance = json.createObjectNode().put("extractedBy", "user");
        return TenantContext.callAsSystem(UUID.fromString(companyId), () -> memoryStore.ingest(
                new MemoryWrite(UUID.fromString(companyId), scope,
                        agentId != null ? UUID.fromString(agentId) : null, roleKey, null, kind, content,
                        (short) 1, status, provenance, null)));
    }

    // ── helpers ──────────────────────────────────────────────────────────

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

    private String hireAgent(String companyId, String templateKey, Integer contextBudgetTokens) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "name", "Agent-" + UUID.randomUUID().toString().substring(0, 6),
                "roleTemplateKey", templateKey,
                "roleTitle", "Title",
                "skillTags", List.of(templateKey),
                "modelProvider", "anthropic",
                "modelName", "claude-sonnet-5"));
        if (contextBudgetTokens != null) {
            body.put("runtimeConfig", Map.of("contextBudgetTokens", contextBudgetTokens));
        }
        ResponseEntity<String> response = rest.postForEntity("/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(body, headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private String createTask(String companyId, String skill) {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", "A task", "requiredSkill", skill), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("task").get("id").asText();
    }

    private void detach(String companyId, String agentId, UUID skillId) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/agents/" + agentId + "/skills/" + skillId, HttpMethod.DELETE,
                new HttpEntity<>(headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private void attach(String companyId, String agentId, UUID skillId, int proficiency) {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/agents/" + agentId + "/skills",
                new HttpEntity<>(Map.of("skillId", skillId.toString(), "proficiency", proficiency),
                        headers(companyId)), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private Skill createCompanySkill(String companyId, String name, String description, String bodyMd) {
        return inTx(companyId, () -> skillRepository.save(new Skill(
                UUID.fromString(companyId), "skill-" + UUID.randomUUID().toString().substring(0, 8), 1, name,
                description, bodyMd, "reference", List.of(), "company", "authored", "system")));
    }

    private Agent agent(String companyId, String agentId) {
        return inTx(companyId, () -> agentRepository
                .findByIdAndCompanyId(UUID.fromString(agentId), UUID.fromString(companyId)).orElseThrow());
    }

    private Task task(String companyId, String taskId) {
        return inTx(companyId, () -> taskRepository
                .findByIdAndCompanyId(UUID.fromString(taskId), UUID.fromString(companyId)).orElseThrow());
    }

    private List<UUID> hiredSkillIds(String companyId, String agentId) {
        return inTx(companyId, () -> agentSkillRepository
                .findByAgentId(UUID.fromString(agentId)).stream()
                .filter(a -> "hired".equals(a.getSource())).map(AgentSkill::getSkillId).toList());
    }

    private ContextBundle assembleAs(String companyId, Agent agentEntity, Task taskEntity) {
        return TenantContext.callAsSystem(UUID.fromString(companyId), () -> contextAssembler.assemble(agentEntity, taskEntity));
    }

    // ── fits-all ─────────────────────────────────────────────────────────

    @Test
    void fitsAllSkillsIncludedFullyWithinBudget() {
        String company = createCompany("fits");
        String agentId = hireAgent(company, "coder", null); // default 4000-token budget, tiny seed skills
        String taskId = createTask(company, "coder");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.skills()).hasSize(2);
        assertThat(bundle.skills()).allSatisfy(s -> assertThat(s.indexOnly()).isFalse());
        assertThat(bundle.skills()).extracting(SkillExcerpt::name)
                .containsExactly("Code review checklist", "Output format: unified diff");
        assertThat(bundle.provenanceIds()).containsExactlyElementsOf(hiredSkillIds(company, agentId));
        assertThat(bundle.tokenCount()).isGreaterThan(0);
        assertThat(bundle.memories()).isEmpty();
        assertThat(bundle.knowledge()).isEmpty();
    }

    // ── name+description fallback ───────────────────────────────────────

    @Test
    void fallsBackToNameAndDescriptionWhenBodyDoesNotFit() {
        String company = createCompany("fallback");
        String agentId = hireAgent(company, "coder", 10); // skills budget = (int)(10*0.5) = 5 tokens
        String taskId = createTask(company, "coder");
        for (UUID hiredId : hiredSkillIds(company, agentId)) {
            detach(company, agentId, hiredId); // remove the seeded skills so only our fixture remains
        }

        // name+description = 16 chars -> 4 tokens (fits in 5); + 400-char body -> 104 tokens (doesn't fit)
        Skill skill = createCompanySkill(company, "S".repeat(8), "D".repeat(8), "B".repeat(400));
        attach(company, agentId, skill.getId(), 3);

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.skills()).hasSize(1);
        SkillExcerpt excerpt = bundle.skills().get(0);
        assertThat(excerpt.indexOnly()).isTrue();
        assertThat(excerpt.bodyMd()).isNull();
        assertThat(excerpt.name()).isEqualTo("S".repeat(8));
        assertThat(bundle.provenanceIds()).containsExactly(skill.getId());
        assertThat(bundle.tokenCount()).isEqualTo(4);
    }

    // ── item-granular truncation: drop whole items, keep trying subsequent ones ──

    @Test
    void itemGranularTruncationDropsOversizedItemsAndKeepsSmallerOnes() {
        String company = createCompany("trunc");
        String agentId = hireAgent(company, "coder", 20); // skills budget = 10 tokens
        String taskId = createTask(company, "coder");
        for (UUID hiredId : hiredSkillIds(company, agentId)) {
            detach(company, agentId, hiredId);
        }

        // Doesn't fit even as name+description (proficiency 5 -> evaluated first).
        Skill huge = createCompanySkill(company, "H".repeat(50), "H".repeat(50), "H".repeat(2000));
        attach(company, agentId, huge.getId(), 5);
        // Fits fully (proficiency 1 -> evaluated after, but still reached since the
        // huge item is skipped rather than aborting the whole loop).
        Skill small = createCompanySkill(company, "sm", "ok", "tiny body");
        attach(company, agentId, small.getId(), 1);

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.skills()).hasSize(1);
        assertThat(bundle.skills().get(0).skillId()).isEqualTo(small.getId());
        assertThat(bundle.skills().get(0).indexOnly()).isFalse();
        assertThat(bundle.provenanceIds()).containsExactly(small.getId());
    }

    // ── ordering: role-attached position order, then own skills by proficiency desc ──

    @Test
    void orderingIsRolePositionThenProficiencyDescending() {
        String company = createCompany("order");
        String agentId = hireAgent(company, "coder", null); // 2 hired, seeded at role position 0, 1
        String taskId = createTask(company, "coder");

        Skill lowProficiency = createCompanySkill(company, "Low", "low prof", "body");
        attach(company, agentId, lowProficiency.getId(), 2);
        Skill highProficiency = createCompanySkill(company, "High", "high prof", "body");
        attach(company, agentId, highProficiency.getId(), 5);

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        // first two are the hired pair, in role-position order (V5 seed: 0=code-review-checklist,
        // 1=unified-diff-output); same names asserted in fitsAllSkillsIncludedFullyWithinBudget.
        assertThat(bundle.skills().subList(0, 2)).extracting(SkillExcerpt::name)
                .containsExactly("Code review checklist", "Output format: unified diff");
        // then the agent's own skills, proficiency 5 before proficiency 2
        assertThat(bundle.provenanceIds().get(2)).isEqualTo(highProficiency.getId());
        assertThat(bundle.provenanceIds().get(3)).isEqualTo(lowProficiency.getId());
        assertThat(bundle.skills()).hasSize(4);
    }

    // ── trust level: only platform/company skills may ever reach a prompt ──

    @Test
    void agentProposedTrustLevelNeverReachesTheBundle() {
        String company = createCompany("trust");
        String agentId = hireAgent(company, "coder", null);
        String taskId = createTask(company, "coder");
        for (UUID hiredId : hiredSkillIds(company, agentId)) {
            detach(company, agentId, hiredId);
        }

        Skill proposed = inTx(company, () -> skillRepository.save(
                new Skill(UUID.fromString(company), "proposed-skill", 1,
                        "Proposed", "Not yet promoted", "body", "reference", List.of(), "agent_proposed",
                        "authored", "agent:" + agentId)));
        inTx(company, () -> agentSkillRepository.save(
                new AgentSkill(UUID.fromString(agentId), proposed.getId(), "learned", (short) 5)));

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.skills()).isEmpty();
        assertThat(bundle.provenanceIds()).isEmpty();
    }

    // ── budget: min(configured, 30% of catalog context window) ─────────────

    @Test
    void budgetIsConfiguredValueWhenBelowTheHardCap() {
        String company = createCompany("budget");
        // claude-sonnet-5's seeded context window is 1,000,000 -> 30% hard cap is
        // 300,000 tokens, far above any configured value we'd set here.
        String agentId = hireAgent(company, "coder", 40); // skills budget = 20 tokens
        String taskId = createTask(company, "coder");
        for (UUID hiredId : hiredSkillIds(company, agentId)) {
            detach(company, agentId, hiredId);
        }
        Skill skill = createCompanySkill(company, "abcd", "efgh", "ijklmnop"); // 16 chars -> 4 tokens, fits 20
        attach(company, agentId, skill.getId(), 3);

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.skills()).hasSize(1);
        assertThat(bundle.skills().get(0).indexOnly()).isFalse();
        assertThat(bundle.tokenCount()).isEqualTo(4);
    }

    // ── determinism: same inputs -> byte-identical bundle ───────────────────

    @Test
    void sameInputsProduceAnIdenticalBundle() {
        String company = createCompany("determ");
        String agentId = hireAgent(company, "research", null);
        String taskId = createTask(company, "research");

        Agent agentEntity = agent(company, agentId);
        Task taskEntity = task(company, taskId);
        ContextBundle first = assembleAs(company, agentEntity, taskEntity);
        ContextBundle second = assembleAs(company, agentEntity, taskEntity);

        assertThat(first).isEqualTo(second);
    }

    // ── memories (M-MEM1): recall, scope/status isolation, truncation, ordering ──

    @Test
    void companyScopePreferenceIsRecalledIntoTheBundle() {
        String company = createCompany("mem-recall");
        String agentId = hireAgent(company, "coder", null);
        String taskId = createTask(company, "coder");

        UUID memoryId = seedMemory(company, "company", null, null, "preference",
                "CEO prefers bullet lists", "active");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.memories()).extracting(h -> h.memory().content())
                .contains("CEO prefers bullet lists");
        assertThat(bundle.provenanceIds()).contains(memoryId);
    }

    @Test
    void agentScopeMemoryIsIsolatedPerAgent() {
        String company = createCompany("mem-agent-iso");
        String agentA = hireAgent(company, "coder", null);
        String agentB = hireAgent(company, "coder", null);
        String taskForB = createTask(company, "coder");

        seedMemory(company, "agent", agentA, null, "fact", "Agent A's private memory", "active");

        ContextBundle bundleForB = assembleAs(company, agent(company, agentB), task(company, taskForB));

        assertThat(bundleForB.memories()).extracting(h -> h.memory().content())
                .doesNotContain("Agent A's private memory");
    }

    @Test
    void companyScopeMemoryIsIsolatedPerCompany() {
        String companyA = createCompany("mem-co-a");
        String companyB = createCompany("mem-co-b");
        String agentInB = hireAgent(companyB, "coder", null);
        String taskInB = createTask(companyB, "coder");

        seedMemory(companyA, "company", null, null, "fact", "Company A's secret fact", "active");

        ContextBundle bundleForB =
                assembleAs(companyB, agent(companyB, agentInB), task(companyB, taskInB));

        assertThat(bundleForB.memories()).extracting(h -> h.memory().content())
                .doesNotContain("Company A's secret fact");
    }

    @Test
    void onlyActiveMemoriesAreRecalled() {
        String company = createCompany("mem-status");
        String agentId = hireAgent(company, "coder", null);
        String taskId = createTask(company, "coder");

        seedMemory(company, "company", null, null, "fact", "still pending review", "pending_review");
        seedMemory(company, "company", null, null, "fact", "already archived", "archived");
        seedMemory(company, "company", null, null, "fact", "rejected content", "rejected");
        seedMemory(company, "company", null, null, "fact", "the active one", "active");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.memories()).extracting(h -> h.memory().content())
                .containsExactly("the active one");
    }

    @Test
    void belowThresholdMemoriesAreExcludedFromRecall() {
        String company = createCompany("mem-threshold");
        String agentId = hireAgent(company, "coder", null);
        String taskId = createTask(company, "coder");
        // query embeds to SIMILAR_VECTOR (task title "A task"); this memory embeds
        // orthogonal -> similarity 0 -> composite score capped at 0.25, below the 0.30 threshold.
        when(embeddingClient.embed(eq("unrelated content"))).thenReturn(DISSIMILAR_VECTOR);
        seedMemory(company, "company", null, null, "fact", "unrelated content", "active");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.memories()).isEmpty();
    }

    @Test
    void itemGranularTruncationDropsOversizedMemoriesAndKeepsSmallerOnes() {
        String company = createCompany("mem-trunc");
        // total budget 20 -> memories budget = (int)(20*0.35) = 7 tokens
        String agentId = hireAgent(company, "coder", 20);
        String taskId = createTask(company, "coder");

        seedMemory(company, "company", null, null, "fact", "M".repeat(200), "active"); // way over 7 tokens
        seedMemory(company, "company", null, null, "fact", "tiny", "active"); // 1 token, fits

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.memories()).extracting(h -> h.memory().content()).containsExactly("tiny");
    }

    @Test
    void preferencesAndLessonsAreOrderedBeforeFacts() {
        String company = createCompany("mem-order");
        String agentId = hireAgent(company, "coder", null);
        String taskId = createTask(company, "coder");

        seedMemory(company, "company", null, null, "fact", "a plain fact", "active");
        seedMemory(company, "company", null, null, "preference", "a strong preference", "active");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.memories()).extracting(h -> h.memory().content())
                .containsExactly("a strong preference", "a plain fact");
    }

    // ── knowledge (M-KN1): role attach respected, threshold, item-granular truncation ──

    @Test
    void attachedKnowledgeDocIsRecalledForItsOwnRoleOnly() {
        String company = createCompany("kn-recall");
        UUID roleId = createRole(company, "content-writer-" + UUID.randomUUID().toString().substring(0, 6));
        UUID docId = ingestKnowledgeDoc(company, "Brand guide", "Our brand voice is friendly and concise.");
        attachKnowledgeToRole(company, roleId, docId);
        String agentId = hireAgentWithCustomRole(company, roleId, "content");
        String taskId = createTask(company, "content");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.knowledge()).extracting(KnowledgeHit::content)
                .containsExactly("Our brand voice is friendly and concise.");
        assertThat(bundle.knowledge().get(0).docTitle()).isEqualTo("Brand guide");
        assertThat(bundle.provenanceIds()).contains(bundle.knowledge().get(0).chunkId());
    }

    @Test
    void unrelatedRoleNeverRecallsAnAttachedDoc() {
        String company = createCompany("kn-unrelated");
        UUID contentRoleId = createRole(company, "content-writer-" + UUID.randomUUID().toString().substring(0, 6));
        UUID docId = ingestKnowledgeDoc(company, "Brand guide", "Our brand voice is friendly and concise.");
        attachKnowledgeToRole(company, contentRoleId, docId);

        // a different role's agent -> the doc was never attached to ITS role
        String agentId = hireAgent(company, "coder", null);
        String taskId = createTask(company, "coder");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.knowledge()).isEmpty();
    }

    @Test
    void belowThresholdKnowledgeChunksAreExcludedFromRecall() {
        String company = createCompany("kn-threshold");
        UUID roleId = createRole(company, "content-writer-" + UUID.randomUUID().toString().substring(0, 6));
        // query embeds to SIMILAR_VECTOR (task title "A task"); this doc's single chunk embeds
        // orthogonal -> plain cosine similarity 0.0, below the 0.35 threshold.
        when(embeddingClient.embed(eq(List.of("unrelated reference text"))))
                .thenReturn(List.of(DISSIMILAR_VECTOR));
        UUID docId = ingestKnowledgeDoc(company, "Unrelated doc", "unrelated reference text");
        attachKnowledgeToRole(company, roleId, docId);
        String agentId = hireAgentWithCustomRole(company, roleId, "content");
        String taskId = createTask(company, "content");

        ContextBundle bundle = assembleAs(company, agent(company, agentId), task(company, taskId));

        assertThat(bundle.knowledge()).isEmpty();
    }
}
