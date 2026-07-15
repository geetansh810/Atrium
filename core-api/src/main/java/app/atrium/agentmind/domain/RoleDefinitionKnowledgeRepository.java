package app.atrium.agentmind.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleDefinitionKnowledgeRepository
        extends JpaRepository<RoleDefinitionKnowledge, RoleDefinitionKnowledge.Key> {

    boolean existsByRoleDefinitionIdAndDocId(UUID roleDefinitionId, UUID docId);
}
