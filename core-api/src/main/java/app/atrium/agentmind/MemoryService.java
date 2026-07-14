package app.atrium.agentmind;

import app.atrium.agentmind.api.MemoryDtos.MemoryBrowseQuery;
import app.atrium.agentmind.api.MemoryDtos.ReviewMemoryRequest;
import app.atrium.agentmind.api.MemoryDtos.SeedMemoryRequest;
import app.atrium.agentmind.domain.Memory;
import app.atrium.agentmind.domain.MemoryRepository;
import app.atrium.common.ConflictException;
import app.atrium.common.FieldValidationException;
import app.atrium.common.KeysetCursors;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import app.atrium.eventbus.OutboxWriter;
import app.atrium.eventbus.Topics;
import app.atrium.registry.AgentDirectory;
import app.atrium.registry.RoleDefinitionLookup;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.RoleDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.lang.Nullable;
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
    private final RoleDefinitionLookup roleDefinitions;
    private final SkillService skillService;
    private final OutboxWriter outboxWriter;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public MemoryService(MemoryRepository memories, MemoryStore memoryStore, AgentDirectory agentDirectory,
                         RoleDefinitionLookup roleDefinitions, SkillService skillService,
                         OutboxWriter outboxWriter, EntityManager entityManager, ObjectMapper objectMapper) {
        this.memories = memories;
        this.memoryStore = memoryStore;
        this.agentDirectory = agentDirectory;
        this.roleDefinitions = roleDefinitions;
        this.skillService = skillService;
        this.outboxWriter = outboxWriter;
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

    /** GET /companies/{id}/memories/review-queue (16 §3) — pending_review items, oldest first. */
    @Transactional(readOnly = true)
    public List<MemoryView> reviewQueue(UUID companyId) {
        return memories.findByCompanyIdAndStatusOrderByCreatedAtAsc(companyId, "pending_review").stream()
                .map(MemoryView::from)
                .toList();
    }

    /**
     * POST /memories/{id}/review (16 §3, 14 §5) — approve/reject a pending
     * item, optionally widening its scope ({@code promoteScope}) and/or
     * spinning off a draft skill ({@code asSkill}); both are independent
     * add-ons available only alongside {@code action:'approve'}.
     */
    @Transactional
    public MemoryView review(UUID companyId, UUID memoryId, ReviewMemoryRequest request) {
        Memory memory = memories.findByIdAndCompanyId(memoryId, companyId)
                .orElseThrow(() -> NotFoundException.of("Memory", memoryId));
        if (!"pending_review".equals(memory.getStatus())) {
            throw new ConflictException("Memory " + memoryId + " is '" + memory.getStatus()
                    + "' — not awaiting review");
        }

        switch (request.action()) {
            case "approve" -> approveReviewed(companyId, memory, request);
            case "reject" -> memoryStore.setStatus(companyId, memoryId, "rejected");
            default -> throw new FieldValidationException(Map.of("action", "must be 'approve' or 'reject'"));
        }

        String reviewer = TenantContext.userId().map(id -> "user:" + id).orElse("system");
        memoryStore.stampReviewed(companyId, memoryId, reviewer);
        // memoryStore's writes above are all raw JDBC (PgVectorMemoryStore's whole-class
        // convention) — Hibernate's first-level cache still holds the `memory` entity as it
        // was BEFORE those writes (loaded via JPA a few lines up), so a plain findById here
        // would silently return that stale snapshot instead of hitting the DB again. Refresh
        // the SAME managed instance instead of re-querying.
        entityManager.refresh(memory);
        return MemoryView.from(memory);
    }

    private void approveReviewed(UUID companyId, Memory memory, ReviewMemoryRequest request) {
        if (request.promoteScope() != null) {
            String toScope = validateScope(request.promoteScope());
            String fromScope = memory.getScope();
            String roleKey = "role".equals(toScope) ? roleKeyForPromotion(companyId, memory) : null;
            UUID agentId = "agent".equals(toScope) ? memory.getAgentId() : null;
            memoryStore.promoteScope(companyId, memory.getId(), toScope, agentId, roleKey);
            publishPromoted(companyId, memory.getId(), memory.getAgentId(), fromScope, toScope);
        }
        memoryStore.setStatus(companyId, memory.getId(), "active");
        if (request.asSkill() != null) {
            skillService.createDraftFromMemory(companyId, request.asSkill().key(),
                    request.asSkill().name(), memory.getContent());
        }
    }

    /** Promoting an agent-scope memory to role scope needs that agent's role key. */
    @Nullable
    private String roleKeyForPromotion(UUID companyId, Memory memory) {
        if (memory.getRoleKey() != null) {
            return memory.getRoleKey();
        }
        if (memory.getAgentId() == null) {
            return null;
        }
        Agent agent = agentDirectory.findById(companyId, memory.getAgentId()).orElse(null);
        if (agent == null) {
            return null;
        }
        RoleDefinition roleDef = roleDefinitions.findById(agent.getRoleDefinitionId()).orElse(null);
        return roleDef != null ? roleDef.getKey() : null;
    }

    private void publishPromoted(UUID companyId, UUID memoryId, @Nullable UUID sourceAgentId,
                                 String fromScope, String toScope) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("memoryId", memoryId.toString());
        payload.put("fromScope", fromScope);
        payload.put("toScope", toScope);
        outboxWriter.append(companyId, Topics.memory(companyId, sourceAgentId), "memory.promoted", payload);
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
