package app.atrium.agentmind;

import app.atrium.agentmind.api.MemoryDtos.MemoryBrowseQuery;
import app.atrium.agentmind.api.MemoryDtos.SeedMemoryRequest;
import app.atrium.agentmind.domain.Memory;
import app.atrium.agentmind.domain.MemoryRepository;
import app.atrium.common.FieldValidationException;
import app.atrium.common.KeysetCursors;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import app.atrium.registry.AgentDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Browse/seed/forget (16 §3) — the human-facing half of memories; recall lives
 * in {@link MemoryStore}/{@link PgVectorMemoryStore}, called directly by
 * {@link SkillContextAssembler}, not through here.
 */
@Service
public class MemoryService {

    private static final Set<String> SCOPES = PgVectorMemoryStore.SCOPES;
    private static final Set<String> KINDS = Set.of("fact", "preference", "lesson", "summary");
    private static final Set<String> STATUSES = Set.of("active", "pending_review", "rejected", "archived");
    private static final int DEFAULT_SEMANTIC_LIMIT = 50;

    private final MemoryRepository memories;
    private final MemoryStore memoryStore;
    private final AgentDirectory agentDirectory;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public MemoryService(MemoryRepository memories, MemoryStore memoryStore, AgentDirectory agentDirectory,
                         EntityManager entityManager, ObjectMapper objectMapper) {
        this.memories = memories;
        this.memoryStore = memoryStore;
        this.agentDirectory = agentDirectory;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /companies/{id}/memories (16 §3). {@code q} present → semantic search
     * via {@link MemoryStore#recall} (no cursor pagination — a deliberate
     * simplification: ranking is by composite score, not creation order, so a
     * keyset cursor on {@code (created_at, id)} wouldn't mean anything here).
     */
    @Transactional(readOnly = true)
    public MemoryPage browse(UUID companyId, MemoryBrowseQuery query) {
        if (query.q() != null && !query.q().isBlank()) {
            return semanticBrowse(companyId, query);
        }
        return keysetBrowse(companyId, query);
    }

    private MemoryPage semanticBrowse(UUID companyId, MemoryBrowseQuery query) {
        Set<String> kinds = query.kind() != null ? Set.of(validateKind(query.kind())) : null;
        List<MemoryHit> hits = memoryStore.recall(new RecallQuery(companyId, query.agentId(), null,
                query.q(), DEFAULT_SEMANTIC_LIMIT, kinds));
        List<MemoryView> views = hits.stream()
                .filter(h -> query.status() == null || query.status().equals(h.memory().status()))
                .filter(h -> query.scope() == null || query.scope().equals(h.memory().scope()))
                .map(MemoryHit::memory)
                .toList();
        return new MemoryPage(views, null);
    }

    private MemoryPage keysetBrowse(UUID companyId, MemoryBrowseQuery query) {
        int limit = clampLimit(query.limit());
        StringBuilder sql = new StringBuilder("SELECT * FROM memories WHERE company_id = :companyId");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId);

        if (query.scope() != null) {
            sql.append(" AND scope = :scope");
            params.put("scope", validateScope(query.scope()));
        }
        if (query.agentId() != null) {
            sql.append(" AND agent_id = :agentId");
            params.put("agentId", query.agentId());
        }
        if (query.kind() != null) {
            sql.append(" AND kind = :kind");
            params.put("kind", validateKind(query.kind()));
        }
        if (query.status() != null) {
            sql.append(" AND status = :status");
            params.put("status", validateStatus(query.status()));
        }
        if (query.cursor() != null) {
            KeysetCursors.Position position = KeysetCursors.decode(query.cursor());
            sql.append(" AND (created_at, id) < (:cursorCreatedAt, :cursorId)");
            params.put("cursorCreatedAt", position.createdAt());
            params.put("cursorId", position.id());
        }
        sql.append(" ORDER BY created_at DESC, id DESC");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), Memory.class);
        params.forEach(nativeQuery::setParameter);
        nativeQuery.setMaxResults(limit + 1);

        @SuppressWarnings("unchecked")
        List<Memory> page = nativeQuery.getResultList();
        String nextCursor = null;
        if (page.size() > limit) {
            page = page.subList(0, limit);
            Memory last = page.get(limit - 1);
            nextCursor = KeysetCursors.encode(last.getCreatedAt(), last.getId());
        }
        return new MemoryPage(page.stream().map(MemoryView::from).toList(), nextCursor);
    }

    /** POST /companies/{id}/memories (16 §3) — human-authored, active immediately, no review gate. */
    @Transactional
    public MemoryView seed(UUID companyId, SeedMemoryRequest request) {
        validateScope(request.scope());
        validateKind(request.kind());
        if ("agent".equals(request.scope())) {
            if (request.agentId() == null) {
                throw new FieldValidationException(Map.of("agentId", "required when scope='agent'"));
            }
            agentDirectory.findById(companyId, request.agentId())
                    .orElseThrow(() -> NotFoundException.of("Agent", request.agentId()));
        } else if ("role".equals(request.scope()) && request.roleKey() == null) {
            throw new FieldValidationException(Map.of("roleKey", "required when scope='role'"));
        }

        ObjectNode provenance = objectMapper.createObjectNode();
        provenance.put("extractedBy", "user");
        String actor = TenantContext.userId().map(id -> "user:" + id).orElse("system");
        provenance.put("createdBy", actor);

        UUID id = memoryStore.ingest(new MemoryWrite(companyId, request.scope(), request.agentId(),
                request.roleKey(), null, request.kind(), request.content(), (short) 1, "active",
                provenance, null));
        return MemoryView.from(memories.findById(id).orElseThrow());
    }

    /** DELETE /memories/{id} — archive, never hard-delete (09 audit posture). */
    @Transactional
    public void forget(UUID companyId, UUID memoryId) {
        memories.findByIdAndCompanyId(memoryId, companyId)
                .orElseThrow(() -> NotFoundException.of("Memory", memoryId));
        memoryStore.forget(companyId, memoryId);
    }

    private String validateScope(String scope) {
        if (!SCOPES.contains(scope)) {
            throw new FieldValidationException(Map.of("scope", "must be one of " + SCOPES));
        }
        return scope;
    }

    private String validateKind(String kind) {
        if (!KINDS.contains(kind)) {
            throw new FieldValidationException(Map.of("kind", "must be one of " + KINDS));
        }
        return kind;
    }

    private String validateStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new FieldValidationException(Map.of("status", "must be one of " + STATUSES));
        }
        return status;
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        if (limit < 1 || limit > 200) {
            throw new FieldValidationException(Map.of("limit", "must be between 1 and 200"));
        }
        return limit;
    }

    public record MemoryPage(List<MemoryView> data, String nextCursor) {}
}
