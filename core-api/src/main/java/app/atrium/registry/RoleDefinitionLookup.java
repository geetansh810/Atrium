package app.atrium.registry;

import app.atrium.registry.domain.RoleDefinition;
import java.util.Optional;
import java.util.UUID;

/**
 * Role-definition lookup for other modules (execution's PromptAssembler at
 * M0.5b) — same pattern as {@link AgentDirectory}/{@link ModelCatalogLookup}:
 * service interface, never the repository. No company param: callers already
 * hold a trusted Agent row whose roleDefinitionId FK is internal data, not
 * user input.
 */
public interface RoleDefinitionLookup {

    Optional<RoleDefinition> findById(UUID roleDefinitionId);
}
