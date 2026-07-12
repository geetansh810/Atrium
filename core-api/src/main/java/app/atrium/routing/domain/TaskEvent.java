package app.atrium.routing.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * APPEND-ONLY audit row (03 invariant 1) — no setters, never UPDATE/DELETE.
 * Written exclusively by TaskEventRecorder in the same tx as the change it records.
 */
@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    /** created|claimed|progress|note|flagged|completed|approved|rejected|requeued|cancelled. */
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /** 'user:&lt;id&gt;' | 'agent:&lt;id&gt;' | 'system'. */
    @Column(nullable = false, updatable = false)
    private String actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private JsonNode payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TaskEvent() {}

    public TaskEvent(UUID companyId, UUID taskId, String eventType, String actor, JsonNode payload) {
        this.companyId = companyId;
        this.taskId = taskId;
        this.eventType = eventType;
        this.actor = actor;
        this.payload = payload;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getTaskId() { return taskId; }
    public String getEventType() { return eventType; }
    public String getActor() { return actor; }
    public JsonNode getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
