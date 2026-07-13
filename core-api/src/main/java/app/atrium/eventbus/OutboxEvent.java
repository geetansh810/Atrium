package app.atrium.eventbus;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of the transactional outbox (15 §2). Write-once by {@link OutboxWriter};
 * only the relay (M0.75) ever touches {@code published_at}. The BIGINT identity
 * id is the ordered cursor key for durable consumers — deliberately not a UUID.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** NULL = pending relay. Stamped by OutboxRelay (M0.75), never by producers. */
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    OutboxEvent(DomainEvent event) {
        this.companyId = event.companyId();
        this.topic = event.topic();
        this.eventType = event.eventType();
        this.payload = event.payload();
    }

    /** Called only by {@link JpaOutboxRelayGateway}, in the same tx as the claiming SELECT. */
    void markPublished(Instant at) {
        this.publishedAt = at;
    }

    public Long getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getTopic() { return topic; }
    public String getEventType() { return eventType; }
    public JsonNode getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
