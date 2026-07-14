package app.atrium.agentmind;

import app.atrium.agentmind.domain.AgentSkill;
import app.atrium.agentmind.domain.AgentSkillRepository;
import app.atrium.agentmind.domain.RoleDefinitionSkill;
import app.atrium.agentmind.domain.RoleDefinitionSkillRepository;
import app.atrium.registry.RoleSkillAttachment;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hire-time auto-attach (14 §1: "hiring from a role template auto-attaches
 * the template's skill set"). Implements registry's {@link RoleSkillAttachment}
 * hook so {@code AgentService.hire()} never imports agentmind types directly —
 * see that interface's javadoc for the boundary reasoning.
 */
@Service
public class SkillAttachmentService implements RoleSkillAttachment {

    private final RoleDefinitionSkillRepository roleDefinitionSkills;
    private final AgentSkillRepository agentSkills;

    public SkillAttachmentService(RoleDefinitionSkillRepository roleDefinitionSkills,
                                  AgentSkillRepository agentSkills) {
        this.roleDefinitionSkills = roleDefinitionSkills;
        this.agentSkills = agentSkills;
    }

    @Override
    @Transactional
    public void attachTemplateSkills(UUID companyId, UUID agentId, UUID roleDefinitionId) {
        for (RoleDefinitionSkill templateSkill
                : roleDefinitionSkills.findByRoleDefinitionIdOrderByPosition(roleDefinitionId)) {
            if (agentSkills.findByAgentIdAndSkillId(agentId, templateSkill.getSkillId()).isEmpty()) {
                agentSkills.save(new AgentSkill(agentId, templateSkill.getSkillId(), "hired", (short) 3));
            }
        }
    }
}
