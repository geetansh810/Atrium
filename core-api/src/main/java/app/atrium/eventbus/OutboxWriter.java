package app.atrium.eventbus;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY way business code publishes events (12 §10 rule 1: no direct Redis
 * publishes). Appends to {@code outbox_events} inside the caller's transaction —
 * {@code MANDATORY} propagation makes an outbox write without a surrounding
 * business transaction a hard error, so the same-tx invariant is structural,
 * not a convention.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository outbox;

    public OutboxWriter(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID companyId, String topic, String eventType, JsonNode payload) {
        append(new DomainEvent(companyId, topic, eventType, payload));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        outbox.save(new OutboxEvent(event));
    }
}
