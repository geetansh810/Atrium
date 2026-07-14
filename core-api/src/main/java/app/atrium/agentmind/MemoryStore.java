package app.atrium.agentmind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portable core SPI (14 §2, Paperclip landscape contract) — v1 impl is
 * {@link PgVectorMemoryStore}; a hosted provider (mem0-style) could bind here
 * later per {@code atrium.memory.provider} without touching callers.
 *
 * <p>Deviates from the doc sketch in one place: no {@code browse(BrowseFilter,
 * Cursor) -> Page<MemoryView>} method here. Browsing is a plain filtered list —
 * it doesn't need vector search unless a caller passes {@code q}, which goes
 * through {@link #recall} instead — so it's implemented directly in
 * {@link MemoryService} over {@code MemoryRepository} (JPA), reusing this
 * codebase's existing {@code PageEnvelope}/{@code KeysetCursors} keyset
 * convention (03 §pagination) rather than introducing a new generic {@code Page}
 * abstraction nothing else in the codebase uses.
 */
public interface MemoryStore {

    /** Embeds {@code w.content()} and inserts — 14 §2. */
    UUID ingest(MemoryWrite w);

    /**
     * Scoped semantic + recency search, {@code status='active'} only (15 §5
     * invariant — only active memories may ever be recalled). Bumps
     * {@code use_count}/{@code last_used_at} on every returned row. Runs a live
     * embedding call for {@code q.queryText()} — if the embedding provider isn't
     * configured, returns an empty list rather than throwing (this executes
     * inside the claim transaction alongside skills assembly, M-CTX1; a config
     * gap must degrade context, never block claiming).
     */
    List<MemoryHit> recall(RecallQuery q);

    /** Archive (never hard-delete, 09 audit posture) — {@code forget} = {@code setStatus(ARCHIVED)}. */
    void forget(UUID companyId, UUID memoryId);

    /** Governance action (approve/reject, M-LN1) — plumbed now, only reachable via {@link #forget} until then. */
    void setStatus(UUID companyId, UUID memoryId, String status);

    /**
     * Dedup probe (14 §5): the best-matching {@code active}/{@code
     * pending_review} row in the EXACT given scope (not {@link #recall}'s
     * scope-set union) with the same {@code kind} — {@code agentId}/{@code
     * roleKey} must match the scope ({@code null} for {@code company}
     * scope). Empty when embeddings aren't configured, the embed call fails,
     * or nothing scores at/above the dedup bar (a much higher bar than
     * {@link #recall}'s 0.30 threshold — see the impl). Callers should bump
     * {@link #bumpImportance} on a hit instead of inserting a new row.
     */
    Optional<DuplicateMatch> findDuplicate(UUID companyId, String scope, UUID agentId, String roleKey,
                                           String kind, String content);

    /** Dedup hit (14 §5): +1 importance, capped at 5, instead of a new insert. */
    void bumpImportance(UUID companyId, UUID memoryId);

    /**
     * Review-approve scope widening (14 §5/16 §3: {@code promoteScope}) —
     * moves a row to {@code role}/{@code company} scope, clearing
     * {@code agentId} and setting {@code roleKey} as appropriate for the new
     * scope. Status is unchanged; callers still call {@link #setStatus}.
     */
    void promoteScope(UUID companyId, UUID memoryId, String newScope, UUID agentId, String roleKey);

    /** Review-action audit stamp (16 §3: {@code provenance.reviewedBy}) — merged, not replaced. */
    void stampReviewed(UUID companyId, UUID memoryId, String reviewedBy);
}
