package app.atrium.agentmind;

import app.atrium.common.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * v1 {@link MemoryStore} impl (14 §2): pgvector cosine search via raw JDBC —
 * no Hibernate vector-type mapping is introduced (see {@code Memory}'s
 * javadoc); vectors are passed as pgvector text literals ({@code "[0.1,0.2,…]"})
 * cast with {@code ::vector} in SQL, the same "raw SQL for Postgres-extension
 * work" idiom {@code WorkBroker}/{@code LeaseReclaimJob} already use for
 * {@code FOR UPDATE SKIP LOCKED}.
 *
 * <p><b>Design call — recall runs a live embedding call inside the caller's
 * transaction.</b> M-CTX1 documented the claimed-event enricher hook as
 * "local-DB-only work" since it only did skills lookups. Memories break that:
 * {@code queryText} is task-specific ({@code task.title+description}), so it
 * can only be embedded once the claim has actually happened and picked a task —
 * there's no way to precompute it earlier and still get a real {@code
 * contextProvenance} list into the SAME {@code claimed} audit event (03
 * invariant 1, append-only, no way to patch payload after commit). Accepted:
 * one small embedding request (~100-300ms) added to the claim's row-lock hold
 * time, versus the LLM completion call that already happens afterward, outside
 * the transaction, at second-scale latency. If this bites in practice
 * (contended skill queues, slow embedding provider), the next lever is async
 * pre-embedding of queued tasks' title+description — out of scope here.
 */
@Component
public class PgVectorMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorMemoryStore.class);

    /** 14 §2 scoring formula, verbatim. */
    private static final double SIMILARITY_WEIGHT = 0.75;
    private static final double RECENCY_WEIGHT = 0.15;
    private static final double USE_COUNT_WEIGHT = 0.10;
    private static final double RECENCY_HALF_LIFE_DAYS = 30.0;
    private static final double SCORE_THRESHOLD = 0.30;
    /** 14 §5 dedup bar — much higher than recall's relevance threshold above. */
    private static final double DUPLICATE_SIMILARITY_THRESHOLD = 0.92;
    static final Set<String> SCOPES = Set.of("agent", "role", "company", "task");

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public PgVectorMemoryStore(JdbcTemplate jdbc, EmbeddingClient embeddingClient, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public UUID ingest(MemoryWrite w) {
        if (!SCOPES.contains(w.scope())) {
            throw new IllegalArgumentException("Unknown memory scope '" + w.scope() + "'");
        }
        UUID id = UUID.randomUUID();
        float[] embedding = embeddingClient.embed(w.content());
        jdbc.update("""
                INSERT INTO memories
                    (id, company_id, scope, agent_id, role_key, task_id, kind, content,
                     embedding, importance, status, provenance, source_event_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?, CAST(? AS jsonb), ?)
                """,
                id, w.companyId(), w.scope(), w.agentId(), w.roleKey(), w.taskId(), w.kind(), w.content(),
                toVectorLiteral(embedding), w.importance(), w.status(), writeJson(w.provenance()),
                w.sourceEventId());
        return id;
    }

    @Override
    @Transactional
    public List<MemoryHit> recall(RecallQuery q) {
        if (!embeddingClient.isReady()) {
            log.warn("Memory recall skipped for company {} — embeddings not configured", q.companyId());
            return List.of();
        }
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingClient.embed(q.queryText());
        } catch (RuntimeException e) {
            log.warn("Memory recall embedding call failed for company {} — degrading to no memories",
                    q.companyId(), e);
            return List.of();
        }

        String vectorLiteral = toVectorLiteral(queryEmbedding);
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM (
                  SELECT m.id, m.company_id, m.scope, m.agent_id, m.role_key, m.task_id, m.kind,
                         m.content, m.importance, m.status, m.provenance, m.source_event_id,
                         m.use_count, m.last_used_at, m.created_at,
                         (? * (1 - (m.embedding <=> CAST(? AS vector)))
                          + ? * POWER(0.5, EXTRACT(EPOCH FROM (now() - m.created_at)) / 86400.0 / ?)
                          + ? * LEAST(m.use_count, 10) / 10.0) AS score
                  FROM memories m
                  WHERE m.company_id = ? AND m.status = 'active' AND m.embedding IS NOT NULL
                    AND ((m.scope = 'agent' AND m.agent_id = ?)
                         OR (m.scope = 'role' AND m.role_key = ?)
                         OR m.scope = 'company')
                """);
        List<Object> params = new ArrayList<>();
        params.add(SIMILARITY_WEIGHT);
        params.add(vectorLiteral);
        params.add(RECENCY_WEIGHT);
        params.add(RECENCY_HALF_LIFE_DAYS);
        params.add(USE_COUNT_WEIGHT);
        params.add(q.companyId());
        params.add(q.agentId());
        params.add(q.roleKey());
        if (q.kinds() != null && !q.kinds().isEmpty()) {
            sql.append(" AND m.kind IN (").append("?,".repeat(q.kinds().size() - 1)).append("?)");
            params.addAll(q.kinds());
        }
        sql.append(") scored WHERE score >= ? ORDER BY score DESC LIMIT ?");
        params.add(SCORE_THRESHOLD);
        params.add(q.k());

        List<MemoryHit> hits = jdbc.query(sql.toString(), this::mapRow, params.toArray());
        bumpUseCount(hits);
        return hits;
    }

    @Override
    @Transactional
    public void forget(UUID companyId, UUID memoryId) {
        setStatus(companyId, memoryId, "archived");
    }

    @Override
    @Transactional
    public void setStatus(UUID companyId, UUID memoryId, String status) {
        int rows = jdbc.update("UPDATE memories SET status = ? WHERE id = ? AND company_id = ?",
                status, memoryId, companyId);
        if (rows == 0) {
            throw NotFoundException.of("Memory", memoryId);
        }
    }

    @Override
    @Transactional
    public Optional<DuplicateMatch> findDuplicate(UUID companyId, String scope, UUID agentId, String roleKey,
                                                  String kind, String content) {
        if (!embeddingClient.isReady()) {
            return Optional.empty();
        }
        float[] embedding;
        try {
            embedding = embeddingClient.embed(content);
        } catch (RuntimeException e) {
            log.warn("Dedup probe skipped for company {} — embedding call failed", companyId, e);
            return Optional.empty();
        }
        String vectorLiteral = toVectorLiteral(embedding);
        List<DuplicateMatch> rows = jdbc.query("""
                SELECT id, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                FROM memories
                WHERE company_id = ? AND scope = ? AND kind = ? AND status IN ('active', 'pending_review')
                  AND embedding IS NOT NULL
                  AND agent_id IS NOT DISTINCT FROM ? AND role_key IS NOT DISTINCT FROM ?
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT 1
                """,
                (rs, rowNum) -> new DuplicateMatch((UUID) rs.getObject("id"), rs.getDouble("similarity")),
                vectorLiteral, companyId, scope, kind, agentId, roleKey, vectorLiteral);
        return rows.stream().filter(m -> m.similarity() >= DUPLICATE_SIMILARITY_THRESHOLD).findFirst();
    }

    @Override
    @Transactional
    public void bumpImportance(UUID companyId, UUID memoryId) {
        jdbc.update("UPDATE memories SET importance = LEAST(importance + 1, 5) WHERE id = ? AND company_id = ?",
                memoryId, companyId);
    }

    @Override
    @Transactional
    public void promoteScope(UUID companyId, UUID memoryId, String newScope, UUID agentId, String roleKey) {
        if (!SCOPES.contains(newScope)) {
            throw new IllegalArgumentException("Unknown memory scope '" + newScope + "'");
        }
        int rows = jdbc.update(
                "UPDATE memories SET scope = ?, agent_id = ?, role_key = ? WHERE id = ? AND company_id = ?",
                newScope, agentId, roleKey, memoryId, companyId);
        if (rows == 0) {
            throw NotFoundException.of("Memory", memoryId);
        }
    }

    @Override
    @Transactional
    public void stampReviewed(UUID companyId, UUID memoryId, String reviewedBy) {
        String provenanceJson = jdbc.queryForObject(
                "SELECT provenance::text FROM memories WHERE id = ? AND company_id = ?",
                String.class, memoryId, companyId);
        if (provenanceJson == null) {
            throw NotFoundException.of("Memory", memoryId);
        }
        ObjectNode provenance = (ObjectNode) readJson(provenanceJson);
        provenance.put("reviewedBy", reviewedBy);
        provenance.put("reviewedAt", Instant.now().toString());
        jdbc.update("UPDATE memories SET provenance = CAST(? AS jsonb) WHERE id = ? AND company_id = ?",
                writeJson(provenance), memoryId, companyId);
    }

    private void bumpUseCount(List<MemoryHit> hits) {
        if (hits.isEmpty()) {
            return;
        }
        List<Object[]> batchArgs = hits.stream()
                .map(h -> new Object[] {h.memory().id()})
                .toList();
        jdbc.batchUpdate("UPDATE memories SET use_count = use_count + 1, last_used_at = now() WHERE id = ?",
                batchArgs);
    }

    private MemoryHit mapRow(ResultSet rs, int rowNum) throws SQLException {
        MemoryView view = new MemoryView(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                rs.getString("scope"),
                (UUID) rs.getObject("agent_id"),
                rs.getString("role_key"),
                (UUID) rs.getObject("task_id"),
                rs.getString("kind"),
                rs.getString("content"),
                rs.getShort("importance"),
                rs.getString("status"),
                readJson(rs.getString("provenance")),
                (Long) rs.getObject("source_event_id"),
                rs.getInt("use_count"),
                toInstant(rs.getTimestamp("last_used_at")),
                toInstant(rs.getTimestamp("created_at")));
        return new MemoryHit(view, rs.getDouble("score"));
    }

    @Nullable
    private static Instant toInstant(@Nullable Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize memory provenance", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse memory provenance", e);
        }
    }
}
