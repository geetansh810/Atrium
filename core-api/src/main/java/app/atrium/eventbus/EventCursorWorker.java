package app.atrium.eventbus;

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

    protected EventCursorWorker(String consumerName, OutboxEventRepository outbox,
                                EventConsumerCursorRepository cursors) {
        this.consumerName = consumerName;
        this.outbox = outbox;
        this.cursors = cursors;
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
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void pollOnce(int batchSize) {
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
