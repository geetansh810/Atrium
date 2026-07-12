package app.atrium.registry;

import app.atrium.registry.domain.Agent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Capability registry — our "AgentCards" (05 §registry). The service interface
 * other modules (routing at M0.3) use for skill lookups; they never touch
 * AgentRepository directly.
 */
public interface AgentDirectory {

    List<Agent> findBySkill(UUID companyId, String skill);

    Optional<Agent> findById(UUID companyId, UUID agentId);
}
