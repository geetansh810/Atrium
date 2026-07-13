package app.atrium.eventbus;

import java.util.List;

/**
 * realtimebridge's doorway into the outbox's own table (12 §3) — the
 * {@code FOR UPDATE SKIP LOCKED} batch claim and the {@code published_at}
 * stamp stay in eventbus so no other module ever touches this table
 * directly. Both methods must run inside the SAME caller transaction:
 * claimed rows stay row-locked (untouched) until {@link #markPublished}
 * commits, so a crash between the two leaves them unpublished for the
 * next relay tick to re-claim.
 */
public interface OutboxRelayGateway {

    /** Claims up to {@code limit} pending rows, oldest first. */
    List<OutboxEvent> claimPending(int limit);

    /** Stamps {@code published_at} on rows already claimed in this transaction. */
    void markPublished(List<OutboxEvent> claimed);
}
