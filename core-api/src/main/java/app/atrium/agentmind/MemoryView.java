package app.atrium.agentmind;

import app.atrium.agentmind.domain.Memory;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Read projection of a {@code memories} row (14 §2) — deliberately excludes the
 * raw {@code embedding} vector, which nothing outside {@link PgVectorMemoryStore}
 * ever needs to see. Used both for browse/inspect (16 §3) and, wrapped in a
 * {@link MemoryHit}, for recall results.
 */
public record MemoryView(UUID id, UUID companyId, String scope, @Nullable UUID agentId, @Nullable String roleKey,
                         @Nullable UUID taskId, String kind, String content, short importance, String status,
                         JsonNode provenance, @Nullable Long sourceEventId, int useCount,
                         @Nullable Instant lastUsedAt, Instant createdAt) {

    public static MemoryView from(Memory m) {
        return new MemoryView(m.getId(), m.getCompanyId(), m.getScope(), m.getAgentId(), m.getRoleKey(),
                m.getTaskId(), m.getKind(), m.getContent(), m.getImportance(), m.getStatus(),
                m.getProvenance(), m.getSourceEventId(), m.getUseCount(), m.getLastUsedAt(), m.getCreatedAt());
    }
}
