package app.atrium.agentmind.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSkillRepository extends JpaRepository<AgentSkill, AgentSkill.Key> {

    List<AgentSkill> findByAgentId(UUID agentId);

    Optional<AgentSkill> findByAgentIdAndSkillId(UUID agentId, UUID skillId);

    void deleteByAgentIdAndSkillId(UUID agentId, UUID skillId);
}
