package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
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

    // ── helpers ──────────────────────────────────────────────────────────

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
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), headers(null)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
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
        return skillRepository.save(new Skill(UUID.fromString(companyId),
                "skill-" + UUID.randomUUID().toString().substring(0, 8), 1, name, description, bodyMd,
                "reference", List.of(), "company", "authored", "system"));
    }

    private Agent agent(String companyId, String agentId) {
        return agentRepository.findByIdAndCompanyId(UUID.fromString(agentId), UUID.fromString(companyId))
                .orElseThrow();
    }

    private Task task(String companyId, String taskId) {
        return taskRepository.findByIdAndCompanyId(UUID.fromString(taskId), UUID.fromString(companyId))
                .orElseThrow();
    }

    private List<UUID> hiredSkillIds(String companyId, String agentId) {
        return agentSkillRepository.findByAgentId(UUID.fromString(agentId)).stream()
                .filter(a -> "hired".equals(a.getSource())).map(AgentSkill::getSkillId).toList();
    }

    // ── fits-all ─────────────────────────────────────────────────────────

    @Test
    void fitsAllSkillsIncludedFullyWithinBudget() {
        String company = createCompany("fits");
        String agentId = hireAgent(company, "coder", null); // default 4000-token budget, tiny seed skills
        String taskId = createTask(company, "coder");

        ContextBundle bundle = contextAssembler.assemble(agent(company, agentId), task(company, taskId));

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

        ContextBundle bundle = contextAssembler.assemble(agent(company, agentId), task(company, taskId));

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

        ContextBundle bundle = contextAssembler.assemble(agent(company, agentId), task(company, taskId));

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

        ContextBundle bundle = contextAssembler.assemble(agent(company, agentId), task(company, taskId));

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

        Skill proposed = skillRepository.save(new Skill(UUID.fromString(company), "proposed-skill", 1,
                "Proposed", "Not yet promoted", "body", "reference", List.of(), "agent_proposed",
                "authored", "agent:" + agentId));
        agentSkillRepository.save(new AgentSkill(UUID.fromString(agentId), proposed.getId(), "learned", (short) 5));

        ContextBundle bundle = contextAssembler.assemble(agent(company, agentId), task(company, taskId));

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

        ContextBundle bundle = contextAssembler.assemble(agent(company, agentId), task(company, taskId));

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
        ContextBundle first = contextAssembler.assemble(agentEntity, taskEntity);
        ContextBundle second = contextAssembler.assemble(agentEntity, taskEntity);

        assertThat(first).isEqualTo(second);
    }
}
