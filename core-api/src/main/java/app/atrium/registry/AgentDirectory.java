package app.atrium.registry;

import app.atrium.registry.domain.Agent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Capability registry — our "AgentCards" (05 §registry). The service interface
 * other modules (routing at M0.3, accountability at M0.7) use for agent
 * lookups and lifecycle side-effects; they never touch AgentRepository directly.
 */
public interface AgentDirectory {

    List<Agent> findBySkill(UUID companyId, String skill);

    Optional<Agent> findById(UUID companyId, UUID agentId);

    /**
     * Hard-cap auto-pause (17 §M0.7, Paperclip): same effect as
     * {@code PATCH paused:true}, minus the request validation an operator
     * hitting the API would go through. No-op if already paused.
     */
    void pauseForBudget(UUID companyId, UUID agentId);
}
