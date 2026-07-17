package app.atrium.eventbus;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for durable outbox consumers (12 §3: "must-process", exactly-once
 * effect via idempotency keys, not delivery guarantees) — poll a batch
 * strictly beyond the consumer's cursor, {@link #handle} each row, advance.
 * No concrete consumer exists yet (M-LN1's {@code LearningPipeline} and
 * M2.3's {@code StatsRollup} are the first); kept generic on purpose.
 *
 * <p>Subclasses drive their own polling (a {@code @Scheduled} method calling
 * {@link #pollOnce}); this class owns only the cursor bookkeeping.
 */
public abstract class EventCursorWorker {

    private final String consumerName;
    private final OutboxEventRepository outbox;
    private final EventConsumerCursorRepository cursors;
    private final EntityManager entityManager;

    protected EventCursorWorker(String consumerName, OutboxEventRepository outbox,
                                EventConsumerCursorRepository cursors, EntityManager entityManager) {
        this.consumerName = consumerName;
        this.outbox = outbox;
        this.cursors = cursors;
        this.entityManager = entityManager;
    }

    /** Exposed for tests that need to seed/inspect this consumer's cursor row directly. */
    protected final String consumerName() {
        return consumerName;
    }

    /**
     * Polls one batch beyond the current cursor, handles each row in order,
     * and advances the cursor to the last handled id. A no-op when there is
     * nothing new. Runs in one transaction: a failure mid-batch rolls the
     * cursor back too, so the next poll re-delivers from the same point
     * (subclasses must make {@link #handle} idempotent).
     *
     * <p>M3.2: the first statement sets {@code app.bypass_rls} for the rest of
     * this transaction — a batch spans every company's outbox rows by
     * construction (that's the whole point of a durable consumer), so this is
     * genuinely cross-tenant system infra, same as {@code LeaseReclaimJob}/
     * {@code OutboxRelay} (08 §Security rule 6). {@code set_config(..., true)}
     * takes effect for every statement issued AFTER it within the current
     * transaction (unlike the tenant-bound path, which stamps {@code
     * app.company_id} once at transaction-begin via {@code
     * TenantAwareJpaTransactionManager} — there's no self-reference/ordering
     * trap here since this method sets its own GUC directly, not through
     * another proxied call). Each {@link #handle} call still writes only to
     * the rows its own event's {@code companyId} names.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void pollOnce(int batchSize) {
        entityManager.createNativeQuery("SELECT set_config('app.bypass_rls', 'on', true)").getSingleResult();
        long cursor = cursors.findById(consumerName).map(EventConsumerCursor::getLastEventId).orElse(0L);
        List<OutboxEvent> batch = outbox.findByIdGreaterThanOrderByIdAsc(cursor, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            handle(event);
        }
        long newCursor = batch.get(batch.size() - 1).getId();
        cursors.findById(consumerName)
                .map(existing -> { existing.advanceTo(newCursor); return existing; })
                .orElseGet(() -> cursors.save(new EventConsumerCursor(consumerName, newCursor)));
    }

    /** Handle one event. Must be idempotent — at-least-once delivery. */
    protected abstract void handle(OutboxEvent event);
}
