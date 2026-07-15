package app.atrium.routing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Exact-once child fan-out per accepted plan (12 §6, 15 §3). The
 * {@code UNIQUE(parent_task_id, plan_artifact_id)} constraint is the
 * fingerprint: {@code plan_artifact_id} is the artifact the parent task's own
 * completion already wrote (M0.5b), so re-processing that same artifact (e.g.
 * a future replay) is a DB-level no-op rather than a duplicate child fan-out.
 */
@Entity
@Table(name = "task_decompositions")
public class TaskDecomposition {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "parent_task_id", nullable = false)
    private UUID parentTaskId;

    @Column(name = "plan_artifact_id", nullable = false)
    private UUID planArtifactId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "child_task_ids", nullable = false, columnDefinition = "uuid[]")
    private List<UUID> childTaskIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TaskDecomposition() {}

    public TaskDecomposition(UUID companyId, UUID parentTaskId, UUID planArtifactId, String createdBy) {
        this.companyId = companyId;
        this.parentTaskId = parentTaskId;
        this.planArtifactId = planArtifactId;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getParentTaskId() { return parentTaskId; }
    public UUID getPlanArtifactId() { return planArtifactId; }
    public String getCreatedBy() { return createdBy; }
    public List<UUID> getChildTaskIds() { return childTaskIds; }
    public Instant getCreatedAt() { return createdAt; }

    public void setChildTaskIds(List<UUID> childTaskIds) {
        this.childTaskIds = childTaskIds;
    }
}
