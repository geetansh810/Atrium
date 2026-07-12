package app.atrium.registry.api;

import app.atrium.registry.domain.Agent;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AgentDtos {

    private AgentDtos() {}

    /** Body per 04 §Registry + 16 §1 — exactly one of roleDefinitionId / roleTemplateKey. */
    public record HireAgentRequest(
            @NotBlank String name,
            String spriteKey,
            UUID roleDefinitionId,
            String roleTemplateKey,
            @NotBlank String roleTitle,
            @NotEmpty List<String> skillTags,
            @NotBlank String modelProvider,
            @NotBlank String modelName,
            UUID managerAgentId,
            String about,
            String runtimeType,
            JsonNode runtimeConfig) {}

    /** Partial update — null fields untouched. paused stops runtime + refuses claims (16 §1). */
    public record PatchAgentRequest(
            String name,
            String spriteKey,
            String roleTitle,
            List<String> skillTags,
            String modelProvider,
            String modelName,
            UUID managerAgentId,
            String status,
            String about,
            String runtimeType,
            JsonNode runtimeConfig,
            Boolean paused) {}

    public record AgentResponse(
            UUID id,
            UUID companyId,
            String name,
            String spriteKey,
            UUID roleDefinitionId,
            String roleTitle,
            List<String> skillTags,
            String modelProvider,
            String modelName,
            UUID managerAgentId,
            String status,
            String about,
            Instant joinedAt,
            String runtimeType,
            JsonNode runtimeConfig,
            boolean paused,
            String currentActivity) {

        public static AgentResponse from(Agent agent) {
            // currentActivity derives from the agent's active task — lands with M0.3
            return new AgentResponse(agent.getId(), agent.getCompanyId(), agent.getName(),
                    agent.getSpriteKey(), agent.getRoleDefinitionId(), agent.getRoleTitle(),
                    agent.getSkillTags(), agent.getModelProvider(), agent.getModelName(),
                    agent.getManagerAgentId(), agent.getStatus(), agent.getAbout(),
                    agent.getJoinedAt(), agent.getRuntimeType(), agent.getRuntimeConfig(),
                    agent.isPaused(), null);
        }
    }

    /** GET /agents/{id}/profile — stats/currentTasks/activityFeed fill in at M0.3/M2.3. */
    public record AgentProfileResponse(
            AgentResponse agent,
            ProfileStats stats,
            List<String> skills,
            List<Object> currentTasks,
            List<Object> activityFeed) {}

    public record ProfileStats(int tasksCompleted, double successRate, int focusMinutes) {

        public static ProfileStats zero() {
            return new ProfileStats(0, 0.0, 0);
        }
    }
}
