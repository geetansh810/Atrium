package app.atrium.agentmind;

import app.atrium.agentmind.api.KnowledgeDtos.IngestKnowledgeRequest;
import app.atrium.agentmind.domain.KnowledgeDoc;
import app.atrium.agentmind.domain.KnowledgeDocRepository;
import app.atrium.agentmind.domain.RoleDefinitionKnowledge;
import app.atrium.agentmind.domain.RoleDefinitionKnowledgeRepository;
import app.atrium.common.FieldValidationException;
import app.atrium.common.NotFoundException;
import app.atrium.registry.RoleDefinitionLookup;
import app.atrium.registry.domain.RoleDefinition;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Knowledge ingestion/browse/archive/role-attach + recall (14 §3, 16 §3).
 * Chunk rows carry an {@code embedding vector(1536)} column with no Hibernate
 * mapping — same "raw JDBC for the vector column" split {@code Memory}/{@link
 * PgVectorMemoryStore} already established — but unlike memories, there's no
 * separate portable-{@code Store} SPI here: 14 §3 doesn't describe knowledge as
 * a swappable Paperclip-landscape contract the way 14 §2 does for memory, so
 * this one class both validates requests and does the raw-JDBC chunk work,
 * called directly by {@link SkillContextAssembler} (no interface indirection).
 *
 * <p><b>Ingest is all-or-nothing.</b> {@code knowledge_docs.status} allows
 * {@code ingesting}/{@code failed} (15 §4.3 CHECK) for a future async pipeline,
 * but v1 is synchronous and minimal (14 §3): the doc row and its chunk rows are
 * written in one transaction, so a failed embedding call (e.g. no
 * {@code OPENAI_API_KEY}) rolls back the whole insert rather than leaving a
 * half-ingested doc behind — no separate failure-visibility mechanism is built
 * this milestone.
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    /** 14 §6 step 3: top-3, score >= 0.35 — plain cosine similarity, no recency/use-count blend. */
    static final int RECALL_K = 3;
    static final double SCORE_THRESHOLD = 0.35;
    private static final Set<String> STATUSES = Set.of("active", "archived");

    private final KnowledgeDocRepository docs;
    private final RoleDefinitionKnowledgeRepository roleDefinitionKnowledge;
    private final RoleDefinitionLookup roleDefinitionLookup;
    private final EmbeddingClient embeddingClient;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public KnowledgeService(KnowledgeDocRepository docs, RoleDefinitionKnowledgeRepository roleDefinitionKnowledge,
                            RoleDefinitionLookup roleDefinitionLookup, EmbeddingClient embeddingClient,
                            JdbcTemplate jdbc, EntityManager entityManager) {
        this.docs = docs;
        this.roleDefinitionKnowledge = roleDefinitionKnowledge;
        this.roleDefinitionLookup = roleDefinitionLookup;
        this.embeddingClient = embeddingClient;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    /** POST /companies/{id}/knowledge (16 §3) — text/markdown only in v1 (14 §3). */
    @Transactional
    public KnowledgeDoc ingest(UUID companyId, IngestKnowledgeRequest request) {
        List<String> chunks = KnowledgeChunker.chunk(request.content());
        if (chunks.isEmpty()) {
            throw new FieldValidationException(Map.of("content", "must contain non-blank text"));
        }
        List<float[]> embeddings = embeddingClient.embed(chunks);

        KnowledgeDoc doc = docs.save(new KnowledgeDoc(companyId, request.title(), request.sourceUri(),
                "text/markdown", "active"));
        // The chunk insert below is raw JDBC on the same connection/transaction but a
        // DIFFERENT write path than Hibernate's session — without an explicit flush here,
        // the doc row Hibernate just staged may not exist on the wire yet, and the chunk
        // insert's FK to knowledge_docs(id) would fail (same "JPA writes are invisible to
        // raw JDBC until flushed" class of bug PgVectorMemoryStore's javadoc warns about,
        // just hit here in the opposite direction: a JPA write needing to precede a raw one).
        entityManager.flush();
        List<Object[]> batchArgs = new ArrayList<>(chunks.size());
        for (int seq = 0; seq < chunks.size(); seq++) {
            batchArgs.add(new Object[] {UUID.randomUUID(), companyId, doc.getId(), seq, chunks.get(seq),
                    toVectorLiteral(embeddings.get(seq))});
        }
        jdbc.batchUpdate("""
                INSERT INTO knowledge_chunks (id, company_id, doc_id, seq, content, embedding)
                VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
                """, batchArgs);
        return doc;
    }

    /** GET /companies/{id}/knowledge (16 §3). */
    @Transactional(readOnly = true)
    public List<KnowledgeDoc> list(UUID companyId, String status) {
        List<KnowledgeDoc> all = docs.findByCompanyIdOrderByCreatedAtDesc(companyId);
        if (status == null) {
            return all;
        }
        validateStatus(status);
        return all.stream().filter(d -> status.equals(d.getStatus())).toList();
    }

    /** DELETE /knowledge/{id} — archive, never hard-delete (09 audit posture, same as memories/skills). */
    @Transactional
    public void archive(UUID companyId, UUID docId) {
        KnowledgeDoc doc = docs.findByIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> NotFoundException.of("Knowledge doc", docId));
        doc.setStatus("archived");
    }

    /**
     * POST /role-definitions/{id}/knowledge (16 §3). Same restriction as
     * {@code SkillService.attachToRole}: only a company's OWN role definition
     * may be attached to — global templates (company_id NULL) are one shared
     * row every tenant hires from, and {@code role_definition_knowledge} has no
     * company_id column of its own, so attaching from a single company's
     * request would leak that company's doc reference into every other
     * tenant's future hires from the same template.
     */
    @Transactional
    public void attachToRole(UUID companyId, UUID roleDefinitionId, UUID docId) {
        RoleDefinition roleDefinition = roleDefinitionLookup.findVisibleToCompany(companyId, roleDefinitionId)
                .filter(rd -> companyId.equals(rd.getCompanyId()))
                .orElseThrow(() -> NotFoundException.of("Role definition", roleDefinitionId));
        KnowledgeDoc doc = docs.findByIdAndCompanyId(docId, companyId)
                .orElseThrow(() -> NotFoundException.of("Knowledge doc", docId));
        if (roleDefinitionKnowledge.existsByRoleDefinitionIdAndDocId(roleDefinition.getId(), doc.getId())) {
            return; // already attached — idempotent
        }
        roleDefinitionKnowledge.save(new RoleDefinitionKnowledge(roleDefinition.getId(), doc.getId()));
    }

    /**
     * 14 §6 step 3: chunks of docs attached to {@code roleDefinitionId} only
     * (role attach is respected, not global — an unrelated role's agents never
     * see a doc that was never attached to their role). Degrades to an empty
     * list rather than throwing when embeddings aren't configured or the embed
     * call fails — same pre-dispatch-gate contract as {@link
     * PgVectorMemoryStore#recall}, since this also runs inside the claim
     * transaction via the same enricher hook.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeHit> recall(UUID companyId, UUID roleDefinitionId, String queryText) {
        if (!embeddingClient.isReady()) {
            return List.of();
        }
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingClient.embed(queryText);
        } catch (RuntimeException e) {
            log.warn("Knowledge recall embedding call failed for company {} — degrading to no knowledge",
                    companyId, e);
            return List.of();
        }
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        return jdbc.query("""
                SELECT * FROM (
                  SELECT c.id AS chunk_id, c.doc_id, d.title AS doc_title, c.seq, c.content,
                         (1 - (c.embedding <=> CAST(? AS vector))) AS score
                  FROM knowledge_chunks c
                  JOIN knowledge_docs d ON d.id = c.doc_id
                  JOIN role_definition_knowledge rdk ON rdk.doc_id = c.doc_id
                  WHERE c.company_id = ? AND d.status = 'active' AND rdk.role_definition_id = ?
                    AND c.embedding IS NOT NULL
                ) scored WHERE score >= ? ORDER BY score DESC LIMIT ?
                """, this::mapRow, vectorLiteral, companyId, roleDefinitionId, SCORE_THRESHOLD, RECALL_K);
    }

    private KnowledgeHit mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeHit((UUID) rs.getObject("chunk_id"), (UUID) rs.getObject("doc_id"),
                rs.getString("doc_title"), rs.getInt("seq"), rs.getString("content"), rs.getDouble("score"));
    }

    private void validateStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new FieldValidationException(Map.of("status", "must be one of " + STATUSES));
        }
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
}
