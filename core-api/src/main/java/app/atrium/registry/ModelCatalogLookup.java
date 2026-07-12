package app.atrium.registry;

import app.atrium.registry.domain.ModelCatalogEntry;
import java.util.Optional;

/**
 * Catalog lookup for other modules (execution's LlmRouter/cost math at M0.5a) —
 * same pattern as {@link AgentDirectory}: service interface, never the repository.
 */
public interface ModelCatalogLookup {

    /** The (provider, model) pair, only if present AND enabled (13 §1.2). */
    Optional<ModelCatalogEntry> findEnabled(String provider, String modelName);
}
