package app.atrium.agentmind;

import app.atrium.agentmind.domain.AgentSkill;
import app.atrium.agentmind.domain.AgentSkillRepository;
import app.atrium.agentmind.domain.RoleDefinitionSkill;
import app.atrium.agentmind.domain.RoleDefinitionSkillRepository;
import app.atrium.agentmind.domain.Skill;
import app.atrium.agentmind.domain.SkillRepository;
import app.atrium.registry.ModelCatalogLookup;
import app.atrium.registry.RoleDefinitionLookup;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.ModelCatalogEntry;
import app.atrium.registry.domain.RoleDefinition;
import app.atrium.routing.domain.Task;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skills + memories + knowledge ContextAssembler (14 §6 steps 1–3, complete as
 * of M-KN1). Only {@code platform}/{@code company} trust-level skills may ever
 * reach a prompt (14 §1, 15 §5 invariant 7); {@code agent_proposed} skills are
 * filtered out here rather than relying on callers to know that rule. Only
 * {@code status='active'} memories may ever be recalled — the same invariant,
 * enforced inside {@link PgVectorMemoryStore#recall}. Knowledge chunks are
 * scoped to docs attached to the agent's own {@code roleDefinitionId} via
 * {@code role_definition_knowledge} — an unrelated role never sees a doc that
 * was never attached to it (14 §3, enforced inside {@link
 * KnowledgeService#recall}).
 *
 * <p><b>Not read-only</b> (unlike M-CTX1's original skills-only version):
 * memory recall bumps {@code use_count}/{@code last_used_at} as a side effect
 * (14 §2), so this method now writes. It still normally runs joined into
 * {@code WorkBroker.claimNext}'s own read-write transaction (via the claimed-
 * event enricher hook) — see {@link PgVectorMemoryStore}'s class javadoc for
 * why the memory recall step also does a live embedding call inside that
 * transaction, a real deviation from M-CTX1's "local-DB-only" note. Knowledge
 * recall is a plain read (no use-count bump) but reuses the same enricher hook
 * for its embedding call, for the identical reason.
 */
@Service
public class SkillContextAssembler implements ContextAssembler {

    private static final Set<String> PROMPTABLE_TRUST_LEVELS = Set.of("platform", "company");
    private static final double SKILLS_BUDGET_SHARE = 0.50;
    private static final double MEMORIES_BUDGET_SHARE = 0.35;
    private static final double KNOWLEDGE_BUDGET_SHARE = 0.15;
    private static final int MEMORIES_RECALL_K = 12;
    private static final int DEFAULT_BUDGET_TOKENS = 4000;
    private static final double HARD_CAP_SHARE_OF_CONTEXT_WINDOW = 0.30;
    /** chars/4 heuristic (14 §6) — documented, not a real tokenizer. */
    private static final int CHARS_PER_TOKEN = 4;
    /** "preferences and lessons before facts" (14 §6) — primary sort key, score desc within each group. */
    private static final Set<String> PRIORITY_MEMORY_KINDS = Set.of("preference", "lesson");

    private final AgentSkillRepository agentSkills;
    private final RoleDefinitionSkillRepository roleDefinitionSkills;
    private final SkillRepository skills;
    private final ModelCatalogLookup modelCatalog;
    private final RoleDefinitionLookup roleDefinitions;
    private final MemoryStore memoryStore;
    private final KnowledgeService knowledgeService;

    public SkillContextAssembler(AgentSkillRepository agentSkills,
                                 RoleDefinitionSkillRepository roleDefinitionSkills,
                                 SkillRepository skills, ModelCatalogLookup modelCatalog,
                                 RoleDefinitionLookup roleDefinitions, MemoryStore memoryStore,
                                 KnowledgeService knowledgeService) {
        this.agentSkills = agentSkills;
        this.roleDefinitionSkills = roleDefinitionSkills;
        this.skills = skills;
        this.modelCatalog = modelCatalog;
        this.roleDefinitions = roleDefinitions;
        this.memoryStore = memoryStore;
        this.knowledgeService = knowledgeService;
    }

    @Override
    @Transactional
    public ContextBundle assemble(Agent agent, Task task) {
        int totalBudget = resolveBudget(agent);
        int skillsBudget = (int) (totalBudget * SKILLS_BUDGET_SHARE);
        List<AgentSkill> ordered = orderedAttachments(agent);

        List<SkillExcerpt> includedSkills = new ArrayList<>();
        List<UUID> provenance = new ArrayList<>();
        int used = 0;
        for (AgentSkill attachment : ordered) {
            Skill skill = skills.findById(attachment.getSkillId()).orElse(null);
            if (skill == null || !PROMPTABLE_TRUST_LEVELS.contains(skill.getTrustLevel())) {
                continue; // stale reference, or a trust level that must never reach a prompt
            }
            int fullTokens = estimateTokens(skill.getName(), skill.getDescription(), skill.getBodyMd());
            if (used + fullTokens <= skillsBudget) {
                includedSkills.add(new SkillExcerpt(skill.getId(), skill.getName(), skill.getDescription(),
                        skill.getBodyMd(), false));
                provenance.add(skill.getId());
                used += fullTokens;
                continue;
            }
            // Whole body didn't fit — try the name+description "skill index" fallback line
            // instead. Truncation is always item-granular: never a partial body_md.
            int indexTokens = estimateTokens(skill.getName(), skill.getDescription(), null);
            if (used + indexTokens <= skillsBudget) {
                includedSkills.add(new SkillExcerpt(skill.getId(), skill.getName(), skill.getDescription(),
                        null, true));
                provenance.add(skill.getId());
                used += indexTokens;
            }
            // else: dropped entirely; keep trying subsequent (possibly smaller) items.
        }

        String queryText = queryText(task);

        int memoriesBudget = (int) (totalBudget * MEMORIES_BUDGET_SHARE);
        List<MemoryHit> includedMemories = recallMemories(agent, queryText, memoriesBudget, provenance);
        used += includedMemories.stream()
                .mapToInt(h -> estimateTokens(null, null, h.memory().content()))
                .sum();

        int knowledgeBudget = (int) (totalBudget * KNOWLEDGE_BUDGET_SHARE);
        List<KnowledgeHit> includedKnowledge = recallKnowledge(agent, queryText, knowledgeBudget, provenance);
        used += includedKnowledge.stream()
                .mapToInt(h -> estimateTokens(null, null, h.content()))
                .sum();

        return new ContextBundle(includedSkills, includedMemories, includedKnowledge, provenance, used);
    }

    private static String queryText(Task task) {
        return task.getDescription() != null && !task.getDescription().isBlank()
                ? task.getTitle() + " " + task.getDescription() : task.getTitle();
    }

    /** 14 §6 step 2: recall(k=12), reorder preference/lesson first, item-granular truncation (no fallback line). */
    private List<MemoryHit> recallMemories(Agent agent, String queryText, int memoriesBudget, List<UUID> provenance) {
        String roleKey = roleDefinitions.findById(agent.getRoleDefinitionId())
                .map(RoleDefinition::getKey).orElse(null);
        List<MemoryHit> hits = memoryStore.recall(
                new RecallQuery(agent.getCompanyId(), agent.getId(), roleKey, queryText, MEMORIES_RECALL_K, null));

        List<MemoryHit> reordered = hits.stream()
                .sorted(Comparator.comparingInt((MemoryHit h) -> priorityRank(h.memory().kind()))
                        .thenComparing(MemoryHit::score, Comparator.reverseOrder()))
                .toList();

        List<MemoryHit> included = new ArrayList<>();
        int used = 0;
        for (MemoryHit hit : reordered) {
            int tokens = estimateTokens(null, null, hit.memory().content());
            if (used + tokens <= memoriesBudget) {
                included.add(hit);
                provenance.add(hit.memory().id());
                used += tokens;
            }
            // else: dropped entirely (no index-line fallback for memories); keep trying smaller items.
        }
        return included;
    }

    private static int priorityRank(String kind) {
        return PRIORITY_MEMORY_KINDS.contains(kind) ? 0 : 1;
    }

    /** 14 §6 step 3: knowledgeService.recall already applies top-3/score>=0.35 and role
     *  scoping; this only does the item-granular budget truncation (no fallback line). */
    private List<KnowledgeHit> recallKnowledge(Agent agent, String queryText, int knowledgeBudget,
                                               List<UUID> provenance) {
        List<KnowledgeHit> hits = knowledgeService.recall(agent.getCompanyId(), agent.getRoleDefinitionId(),
                queryText);

        List<KnowledgeHit> included = new ArrayList<>();
        int used = 0;
        for (KnowledgeHit hit : hits) {
            int tokens = estimateTokens(null, null, hit.content());
            if (used + tokens <= knowledgeBudget) {
                included.add(hit);
                provenance.add(hit.chunkId());
                used += tokens;
            }
            // else: dropped entirely; keep trying smaller items.
        }
        return included;
    }

    /** Role-attached ("hired") skills in the role's position order, then the
     *  agent's own skills by proficiency desc — 14 §6 step 1. */
    private List<AgentSkill> orderedAttachments(Agent agent) {
        Map<UUID, Integer> rolePositions = new LinkedHashMap<>();
        for (RoleDefinitionSkill rds
                : roleDefinitionSkills.findByRoleDefinitionIdOrderByPosition(agent.getRoleDefinitionId())) {
            rolePositions.put(rds.getSkillId(), rds.getPosition());
        }

        List<AgentSkill> attachments = agentSkills.findByAgentId(agent.getId());
        List<AgentSkill> hired = attachments.stream()
                .filter(a -> "hired".equals(a.getSource()))
                .sorted(Comparator.comparingInt(a -> rolePositions.getOrDefault(a.getSkillId(), Integer.MAX_VALUE)))
                .toList();
        List<AgentSkill> own = attachments.stream()
                .filter(a -> !"hired".equals(a.getSource()))
                .sorted(Comparator.comparingInt((AgentSkill a) -> (int) a.getProficiency()).reversed()
                        .thenComparing(AgentSkill::getAttachedAt)
                        .thenComparing(AgentSkill::getSkillId))
                .toList();

        List<AgentSkill> ordered = new ArrayList<>(hired.size() + own.size());
        ordered.addAll(hired);
        ordered.addAll(own);
        return ordered;
    }

    /** {@code runtime_config.contextBudgetTokens} (default 4000), hard-capped at
     *  30% of the model's catalog context window. */
    private int resolveBudget(Agent agent) {
        int configured = DEFAULT_BUDGET_TOKENS;
        JsonNode runtimeConfig = agent.getRuntimeConfig();
        if (runtimeConfig != null && runtimeConfig.has("contextBudgetTokens")) {
            configured = runtimeConfig.get("contextBudgetTokens").asInt(DEFAULT_BUDGET_TOKENS);
        }
        int hardCap = modelCatalog.findEnabled(agent.getModelProvider(), agent.getModelName())
                .map(ModelCatalogEntry::getContextWindow)
                .map(window -> (int) (window * HARD_CAP_SHARE_OF_CONTEXT_WINDOW))
                .orElse(configured);
        return Math.min(configured, hardCap);
    }

    private static int estimateTokens(String name, String description, String bodyMd) {
        int chars = length(name) + length(description) + length(bodyMd);
        return (int) Math.ceil(chars / (double) CHARS_PER_TOKEN);
    }

    private static int length(String s) {
        return s != null ? s.length() : 0;
    }
}
