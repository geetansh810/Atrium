package app.atrium.registry;

import app.atrium.common.NotFoundException;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.AgentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AgentLifecycleService} is injected {@code @Lazy}: it sits behind
 * {@code RuntimeRegistry} → {@code LlmLoopRuntime} → {@code WorkBroker} →
 * this class, so an eager constructor injection here would be a circular
 * bean dependency at context startup. The lazy proxy defers resolution past
 * that point — by the time {@link #pauseForBudget} actually calls it, the
 * context is long since up.
 */
@Service
public class JpaAgentDirectory implements AgentDirectory {

    private final AgentRepository agents;
    private final AgentLifecycleService lifecycle;

    public JpaAgentDirectory(AgentRepository agents, @Lazy AgentLifecycleService lifecycle) {
        this.agents = agents;
        this.lifecycle = lifecycle;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Agent> findBySkill(UUID companyId, String skill) {
        return agents.findByCompanyIdAndSkill(companyId, skill);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Agent> findById(UUID companyId, UUID agentId) {
        return agents.findByIdAndCompanyId(agentId, companyId);
    }

    @Override
    @Transactional
    public void pauseForBudget(UUID companyId, UUID agentId) {
        Agent agent = agents.findByIdAndCompanyId(agentId, companyId)
                .orElseThrow(() -> NotFoundException.of("Agent", agentId));
        if (!agent.isPaused()) {
            agent.setPaused(true);
            lifecycle.stop(agent);
        }
    }
}
