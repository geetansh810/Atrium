package app.atrium.registry.api;

import app.atrium.registry.domain.RoleDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class RoleDefinitionDtos {

    private RoleDefinitionDtos() {}

    public record CreateRoleDefinitionRequest(
            @NotBlank String key,
            @NotBlank String title,
            @NotBlank String systemPrompt,
            JsonNode allowedTools,
            String outputContract,
            Boolean reviewRequired) {}

    public record RoleDefinitionResponse(
            UUID id,
            UUID companyId,
            String key,
            int version,
            String title,
            String systemPrompt,
            JsonNode allowedTools,
            String outputContract,
            boolean globalTemplate,
            boolean reviewRequired) {

        public static RoleDefinitionResponse from(RoleDefinition rd) {
            return new RoleDefinitionResponse(rd.getId(), rd.getCompanyId(), rd.getKey(),
                    rd.getVersion(), rd.getTitle(), rd.getSystemPrompt(), rd.getAllowedTools(),
                    rd.getOutputContract(), rd.isGlobalTemplate(), rd.isReviewRequired());
        }
    }
}
