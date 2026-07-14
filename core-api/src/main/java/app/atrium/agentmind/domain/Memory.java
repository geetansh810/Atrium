package app.atrium.agentmind.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

/**
 * A learned item with provenance (14 §2, table per 15 §4.2). Read-only from
 * JPA's side — deliberately excludes the {@code embedding vector(1536)} column,
 * which only {@link app.atrium.agentmind.PgVectorMemoryStore}'s raw-JDBC paths
 * ever read or write (no Hibernate vector type dependency needed). All writes
 * to this table (ingest/status/use-count) go through that same raw-JDBC path,
 * so there's no risk of a JPA save silently clobbering the vector column.
 */
@Entity
@Table(name = "memories")
public class Memory {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String scope;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "role_key")
    private String roleKey;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private short importance;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode provenance;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Memory() {}

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getScope() { return scope; }
    @Nullable public UUID getAgentId() { return agentId; }
    @Nullable public String getRoleKey() { return roleKey; }
    @Nullable public UUID getTaskId() { return taskId; }
    public String getKind() { return kind; }
    public String getContent() { return content; }
    public short getImportance() { return importance; }
    public String getStatus() { return status; }
    public JsonNode getProvenance() { return provenance; }
    @Nullable public Long getSourceEventId() { return sourceEventId; }
    public int getUseCount() { return useCount; }
    @Nullable public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
