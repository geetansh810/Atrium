package app.atrium.agentmind.api;

import app.atrium.agentmind.MemoryView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

public final class MemoryDtos {

    private MemoryDtos() {}

    public record MemoryBrowseQuery(
            @Nullable String scope,
            @Nullable UUID agentId,
            @Nullable String kind,
            @Nullable String status,
            @Nullable String q,
            @Nullable Integer limit,
            @Nullable String cursor) {}

    /** 16 §3: {scope, agentId?, roleKey?, kind, content} → seeded active immediately (human-authored). */
    public record SeedMemoryRequest(
            @NotBlank String scope,
            @Nullable UUID agentId,
            @Nullable String roleKey,
            @NotBlank String kind,
            @NotBlank String content) {}

    /**
     * 16 §3: {action:'approve'|'reject', promoteScope?:'role'|'company',
     * asSkill?:{key,name}} — POST /memories/{id}/review. promoteScope and
     * asSkill only apply on approve; both are independent optional add-ons
     * (a promoted memory can also become a draft skill in the same call).
     */
    public record ReviewMemoryRequest(
            @NotBlank String action,
            @Nullable String promoteScope,
            @Nullable AsSkillRequest asSkill) {}

    public record AsSkillRequest(@NotBlank String key, @NotBlank String name) {}

    public record MemoryResponse(
            UUID id, UUID companyId, String scope, @Nullable UUID agentId, @Nullable String roleKey,
            @Nullable UUID taskId, String kind, String content, short importance, String status,
            JsonNode provenance, int useCount, @Nullable Instant lastUsedAt, Instant createdAt) {

        public static MemoryResponse from(MemoryView m) {
            return new MemoryResponse(m.id(), m.companyId(), m.scope(), m.agentId(), m.roleKey(), m.taskId(),
                    m.kind(), m.content(), m.importance(), m.status(), m.provenance(), m.useCount(),
                    m.lastUsedAt(), m.createdAt());
        }
    }
}
