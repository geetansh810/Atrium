package app.atrium.registry.api;

import app.atrium.registry.domain.ModelCatalogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public final class ModelCatalogDtos {

    private ModelCatalogDtos() {}

    /** 16 §1: prices are admin-only — deliberately omitted from the v1 response. */
    public record ModelCatalogResponse(
            UUID id,
            String provider,
            String modelName,
            String displayName,
            String tier,
            JsonNode capabilities,
            int contextWindow) {

        public static ModelCatalogResponse from(ModelCatalogEntry entry) {
            return new ModelCatalogResponse(entry.getId(), entry.getProvider(), entry.getModelName(),
                    entry.getDisplayName(), entry.getTier(), entry.getCapabilities(),
                    entry.getContextWindow());
        }
    }
}
