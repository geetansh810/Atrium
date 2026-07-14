package app.atrium.agentmind;

import java.util.List;
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
}
