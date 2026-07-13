package app.atrium.registry;

import app.atrium.registry.domain.RoleDefinition;
import app.atrium.registry.domain.RoleDefinitionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaRoleDefinitionLookup implements RoleDefinitionLookup {

    private final RoleDefinitionRepository roleDefinitions;

    public JpaRoleDefinitionLookup(RoleDefinitionRepository roleDefinitions) {
        this.roleDefinitions = roleDefinitions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleDefinition> findById(UUID roleDefinitionId) {
        return roleDefinitions.findById(roleDefinitionId);
    }
}
