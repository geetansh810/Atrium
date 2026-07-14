package app.atrium.agentmind.api;

import app.atrium.agentmind.domain.Skill;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SkillDtos {

    private SkillDtos() {}

    public record CreateSkillRequest(
            @NotBlank String key,
            @NotBlank String name,
            @NotBlank String description,
            @NotBlank String bodyMd,
            @NotBlank String kind,
            List<String> tags) {}

    /** Same key, next version — name/description/bodyMd required, kind/tags default to the prior version's. */
    public record AddSkillVersionRequest(
            @NotBlank String name,
            @NotBlank String description,
            @NotBlank String bodyMd,
            String kind,
            List<String> tags) {}

    public record AttachAgentSkillRequest(@NotNull UUID skillId, Short proficiency) {}

    public record AttachRoleSkillRequest(@NotNull UUID skillId, Integer position) {}

    public record SkillResponse(
            UUID id,
            UUID companyId,
            String key,
            int version,
            String name,
            String description,
            String bodyMd,
            String kind,
            List<String> tags,
            String trustLevel,
            String source,
            String createdBy,
            Instant createdAt,
            boolean global) {

        public static SkillResponse from(Skill s) {
            return new SkillResponse(s.getId(), s.getCompanyId(), s.getKey(), s.getVersion(), s.getName(),
                    s.getDescription(), s.getBodyMd(), s.getKind(), s.getTags(), s.getTrustLevel(),
                    s.getSource(), s.getCreatedBy(), s.getCreatedAt(), s.isGlobal());
        }
    }
}
