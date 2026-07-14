package app.atrium.agentmind;

import app.atrium.agentmind.api.SkillDtos.AddSkillVersionRequest;
import app.atrium.agentmind.api.SkillDtos.CreateSkillRequest;
import app.atrium.agentmind.domain.AgentSkill;
import app.atrium.agentmind.domain.AgentSkillRepository;
import app.atrium.agentmind.domain.RoleDefinitionSkill;
import app.atrium.agentmind.domain.RoleDefinitionSkillRepository;
import app.atrium.agentmind.domain.Skill;
import app.atrium.agentmind.domain.SkillRepository;
import app.atrium.common.FieldValidationException;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import app.atrium.registry.AgentDirectory;
import app.atrium.registry.RoleDefinitionLookup;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.RoleDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skills CRUD/versioning/attachment (14 §1, 16 §2). Cross-module reads go
 * through {@link AgentDirectory}/{@link RoleDefinitionLookup} only — never
 * registry's repositories directly (12 §2, agentmind → registry).
 */
@Service
public class SkillService {

    private static final Set<String> KINDS = Set.of("procedure", "reference", "tool_guide", "policy");

    private final SkillRepository skills;
    private final RoleDefinitionSkillRepository roleDefinitionSkills;
    private final AgentSkillRepository agentSkills;
    private final AgentDirectory agentDirectory;
    private final RoleDefinitionLookup roleDefinitionLookup;

    public SkillService(SkillRepository skills, RoleDefinitionSkillRepository roleDefinitionSkills,
                        AgentSkillRepository agentSkills, AgentDirectory agentDirectory,
                        RoleDefinitionLookup roleDefinitionLookup) {
        this.skills = skills;
        this.roleDefinitionSkills = roleDefinitionSkills;
        this.agentSkills = agentSkills;
        this.agentDirectory = agentDirectory;
        this.roleDefinitionLookup = roleDefinitionLookup;
    }

    /** Company + global skills, latest version per (scope, key) — 16 §2. */
    @Transactional(readOnly = true)
    public List<Skill> listVisible(UUID companyId, String kind, String tag) {
        Map<String, Skill> latestByScopedKey = new LinkedHashMap<>();
        for (Skill s : skills.findVisibleToCompany(companyId)) {
            String scopedKey = s.getCompanyId() + ":" + s.getKey();
            latestByScopedKey.putIfAbsent(scopedKey, s); // query orders version DESC per key
        }
        return latestByScopedKey.values().stream()
                .filter(s -> kind == null || kind.equals(s.getKind()))
                .filter(s -> tag == null || s.getTags().contains(tag))
                .toList();
    }

    @Transactional
    public Skill create(UUID companyId, CreateSkillRequest request) {
        validateKind(request.kind());
        return skills.save(new Skill(companyId, request.key(), 1, request.name(), request.description(),
                request.bodyMd(), request.kind(), request.tags() != null ? request.tags() : List.of(),
                "company", "authored", actor()));
    }

    /** New version of an existing company-owned key — global templates are seed-only. */
    @Transactional
    public Skill addVersion(UUID companyId, UUID skillId, AddSkillVersionRequest request) {
        Skill existing = skills.findById(skillId)
                .filter(s -> companyId.equals(s.getCompanyId()))
                .orElseThrow(() -> NotFoundException.of("Skill", skillId));
        String kind = request.kind() != null ? request.kind() : existing.getKind();
        validateKind(kind);
        int nextVersion = skills
                .findFirstByCompanyIdAndKeyOrderByVersionDesc(companyId, existing.getKey())
                .map(s -> s.getVersion() + 1)
                .orElse(existing.getVersion() + 1);
        return skills.save(new Skill(companyId, existing.getKey(), nextVersion, request.name(),
                request.description(), request.bodyMd(), kind,
                request.tags() != null ? request.tags() : existing.getTags(),
                existing.getTrustLevel(), existing.getSource(), actor()));
    }

    @Transactional(readOnly = true)
    public Skill get(UUID companyId, UUID skillId) {
        return skills.findByIdVisibleToCompany(skillId, companyId)
                .orElseThrow(() -> NotFoundException.of("Skill", skillId));
    }

    @Transactional
    public void attachToAgent(UUID companyId, UUID agentId, UUID skillId, Short proficiency) {
        requireAgent(companyId, agentId);
        Skill skill = get(companyId, skillId);
        AgentSkill existing = agentSkills.findByAgentIdAndSkillId(agentId, skillId).orElse(null);
        if (existing != null) {
            if (proficiency != null) existing.setProficiency(proficiency);
        } else {
            agentSkills.save(new AgentSkill(agentId, skill.getId(), "assigned",
                    proficiency != null ? proficiency : 3));
        }
    }

    @Transactional
    public void detachFromAgent(UUID companyId, UUID agentId, UUID skillId) {
        requireAgent(companyId, agentId);
        agentSkills.deleteByAgentIdAndSkillId(agentId, skillId);
    }

    /**
     * Design call: only a company's OWN role definition may be attached to —
     * global templates (company_id NULL) are shared rows every tenant hires
     * from, so mutating one from a single company's request would leak that
     * attachment to every other tenant's future hires (violates 09's tenant
     * isolation rule in spirit, even though the row itself isn't cross-tenant
     * READ). 16 §2's literal text doesn't restrict this, but silently leaking
     * skill content across tenants is worse than under-implementing the
     * endpoint for the (out-of-scope-for-M-SK1) template-customization case.
     */
    @Transactional
    public void attachToRole(UUID companyId, UUID roleDefinitionId, UUID skillId, Integer position) {
        RoleDefinition roleDefinition = roleDefinitionLookup.findVisibleToCompany(companyId, roleDefinitionId)
                .filter(rd -> companyId.equals(rd.getCompanyId()))
                .orElseThrow(() -> NotFoundException.of("Role definition", roleDefinitionId));
        Skill skill = get(companyId, skillId);
        if (roleDefinitionSkills.existsByRoleDefinitionIdAndSkillId(roleDefinition.getId(), skill.getId())) {
            return; // already attached — idempotent
        }
        int nextPosition = position != null ? position
                : roleDefinitionSkills.findByRoleDefinitionIdOrderByPosition(roleDefinition.getId()).size();
        roleDefinitionSkills.save(new RoleDefinitionSkill(roleDefinition.getId(), skill.getId(), nextPosition));
    }

    /**
     * Review-approve "convert to draft skill" action (14 §5, 16 §3) — a
     * recurring lesson promoted into a company-scoped, {@code
     * trust_level='agent_proposed'} skill. Never auto-promoted to {@code
     * platform}/{@code company} trust — {@link SkillContextAssembler} only
     * ever includes {@code platform}/{@code company} skills in a prompt (14
     * §1), so this stays inert until a human separately re-authors/promotes
     * it, same as any other {@code agent_proposed} row.
     */
    @Transactional
    public Skill createDraftFromMemory(UUID companyId, String key, String name, String bodyMd) {
        return skills.save(new Skill(companyId, key, 1, name, "Promoted from a reviewed memory.", bodyMd,
                "procedure", List.of(), "agent_proposed", "promoted_memory", actor()));
    }

    /** GET /agents/{id}/mind's skills part (16 §1) — memory/knowledge parts are zeroed until M-MEM1/M-KN1. */
    @Transactional(readOnly = true)
    public List<AgentSkillView> mind(UUID companyId, UUID agentId) {
        requireAgent(companyId, agentId);
        return agentSkills.findByAgentId(agentId).stream()
                .map(as -> new AgentSkillView(skills.findById(as.getSkillId()).orElseThrow(), as))
                .toList();
    }

    public record AgentSkillView(Skill skill, AgentSkill attachment) {}

    private Agent requireAgent(UUID companyId, UUID agentId) {
        return agentDirectory.findById(companyId, agentId)
                .orElseThrow(() -> NotFoundException.of("Agent", agentId));
    }

    private void validateKind(String kind) {
        if (!KINDS.contains(kind)) {
            throw new FieldValidationException(Map.of("kind", "must be one of " + KINDS));
        }
    }

    private String actor() {
        return TenantContext.userId().map(id -> "user:" + id).orElse("system");
    }
}
