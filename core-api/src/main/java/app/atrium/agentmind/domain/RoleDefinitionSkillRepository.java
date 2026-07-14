package app.atrium.agentmind.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleDefinitionSkillRepository
        extends JpaRepository<RoleDefinitionSkill, RoleDefinitionSkill.Key> {

    List<RoleDefinitionSkill> findByRoleDefinitionIdOrderByPosition(UUID roleDefinitionId);

    boolean existsByRoleDefinitionIdAndSkillId(UUID roleDefinitionId, UUID skillId);
}
