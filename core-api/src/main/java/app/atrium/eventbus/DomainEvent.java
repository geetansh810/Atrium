package app.atrium.eventbus;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/**
 * One event bound for the outbox (12 §3). {@code topic} follows the taxonomy
 * in 12 §4 (build via {@link Topics}); {@code eventType} is the existing
 * dot.case vocabulary from 04 §WebSocket.
 */
public record DomainEvent(UUID companyId, String topic, String eventType, JsonNode payload) {

    public DomainEvent {
        if (companyId == null) throw new IllegalArgumentException("companyId is required");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (payload == null) throw new IllegalArgumentException("payload is required");
    }
}
