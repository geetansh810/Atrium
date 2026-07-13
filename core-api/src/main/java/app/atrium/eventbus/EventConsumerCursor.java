package app.atrium.eventbus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row per durable consumer (15 §2) — {@code last_event_id} is its resume point. */
@Entity
@Table(name = "event_consumers")
class EventConsumerCursor {

    @Id
    @Column(name = "consumer_name")
    private String consumerName;

    @Column(name = "last_event_id", nullable = false)
    private long lastEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected EventConsumerCursor() {}

    EventConsumerCursor(String consumerName, long lastEventId) {
        this.consumerName = consumerName;
        this.lastEventId = lastEventId;
    }

    String getConsumerName() { return consumerName; }
    long getLastEventId() { return lastEventId; }

    void advanceTo(long eventId) {
        this.lastEventId = eventId;
        this.updatedAt = Instant.now();
    }
}
