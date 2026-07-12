package app.atrium.routing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "parent_task_id")
    private UUID parentTaskId;

    @Column(name = "required_skill", nullable = false)
    private String requiredSkill;

    @Column(nullable = false)
    private String title;

    private String description;

    /** 1 high … 5 low. */
    @Column(nullable = false)
    private int priority = 3;

    /** Guarded by TaskStateGuard — never set outside a guarded transition. */
    @Column(nullable = false)
    private String status = "queued";

    /** 0–100. */
    @Column(nullable = false)
    private int progress = 0;

    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "assigned_agent_id")
    private UUID assignedAgentId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** ++ on every claim (M0.4); usage idempotency key = taskId:attempt. */
    @Column(nullable = false)
    private int attempt = 0;

    /** Root of the request chain — self for user-created roots, copied from parent (12 §6). */
    @Column(name = "billing_task_id")
    private UUID billingTaskId;

    /** Delegation hops from root. */
    @Column(name = "request_depth", nullable = false)
    private int requestDepth = 0;

    protected Task() {}

    public Task(UUID companyId, UUID parentTaskId, String requiredSkill, String title,
                String description, int priority, Integer etaMinutes, UUID createdByUserId,
                UUID billingTaskId, int requestDepth) {
        this.companyId = companyId;
        this.parentTaskId = parentTaskId;
        this.requiredSkill = requiredSkill;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.etaMinutes = etaMinutes;
        this.createdByUserId = createdByUserId;
        this.billingTaskId = billingTaskId;
        this.requestDepth = requestDepth;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getParentTaskId() { return parentTaskId; }
    public String getRequiredSkill() { return requiredSkill; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getPriority() { return priority; }
    public String getStatus() { return status; }
    public int getProgress() { return progress; }
    public Integer getEtaMinutes() { return etaMinutes; }
    public UUID getAssignedAgentId() { return assignedAgentId; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttempt() { return attempt; }
    public UUID getBillingTaskId() { return billingTaskId; }
    public int getRequestDepth() { return requestDepth; }

    /** Root tasks bill to themselves — callable only once the id exists (post-persist). */
    public void billToSelf() {
        this.billingTaskId = this.id;
    }

    /**
     * Deliberately package-private: status moves only through
     * {@link app.atrium.routing.TaskStateGuard}-checked service code.
     */
    void setStatus(String status) {
        this.status = status;
    }
}
