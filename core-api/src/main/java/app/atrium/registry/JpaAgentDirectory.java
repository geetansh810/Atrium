package app.atrium.registry;

import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.AgentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaAgentDirectory implements AgentDirectory {

    private final AgentRepository agents;

    public JpaAgentDirectory(AgentRepository agents) {
        this.agents = agents;
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
}
