package app.atrium.registry;

import java.util.UUID;

/**
 * Hook called on hire to auto-attach a role template's skill set (14 §1,
 * 16 §2). Defined here (registry) rather than imported from agentmind, since
 * the module boundary (12 §2) is one-directional — {@code agentmind →
 * registry} only. agentmind provides the real implementation; registry's
 * {@link AgentService} depends only on this interface, never on agentmind
 * types, so the dependency graph stays acyclic.
 */
public interface RoleSkillAttachment {

    void attachTemplateSkills(UUID companyId, UUID agentId, UUID roleDefinitionId);
}
