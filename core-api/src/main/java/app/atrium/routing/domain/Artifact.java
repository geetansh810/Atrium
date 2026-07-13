package app.atrium.routing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Agent output (03 §V1). One task can accumulate several across attempts; latest wins for display. */
@Entity
@Table(name = "artifacts")
public class Artifact {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** text | diff | file | report | json. */
    @Column(nullable = false)
    private String kind;

    /** Inline for small; S3 key for large (Phase 3). */
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Artifact() {}

    public Artifact(UUID companyId, UUID taskId, String kind, String content) {
        this.companyId = companyId;
        this.taskId = taskId;
        this.kind = kind;
        this.content = content;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getTaskId() { return taskId; }
    public String getKind() { return kind; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
