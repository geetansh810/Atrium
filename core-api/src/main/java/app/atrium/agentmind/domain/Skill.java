package app.atrium.agentmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A versioned unit of procedure, taught to agents by inclusion in context
 * (14 §1). company_id NULL = global platform skill, visible to every tenant.
 * Editing = a new version row (same UNIQUE(company_id, key, version) idiom
 * as {@link app.atrium.registry.domain.RoleDefinition}); attachments pin a
 * specific version.
 */
@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "body_md", nullable = false)
    private String bodyMd;

    /** procedure | reference | tool_guide | policy. */
    @Column(nullable = false)
    private String kind;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags;

    /** platform | company | agent_proposed — only platform/company may enter prompts. */
    @Column(name = "trust_level", nullable = false)
    private String trustLevel = "company";

    /** authored | template | promoted_memory. */
    @Column(nullable = false)
    private String source = "authored";

    /** 'user:<id>' | 'agent:<id>' | 'system'. */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Skill() {}

    public Skill(UUID companyId, String key, int version, String name, String description,
                 String bodyMd, String kind, List<String> tags, String trustLevel, String source,
                 String createdBy) {
        this.companyId = companyId;
        this.key = key;
        this.version = version;
        this.name = name;
        this.description = description;
        this.bodyMd = bodyMd;
        this.kind = kind;
        this.tags = tags;
        this.trustLevel = trustLevel;
        this.source = source;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getKey() { return key; }
    public int getVersion() { return version; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBodyMd() { return bodyMd; }
    public String getKind() { return kind; }
    public List<String> getTags() { return tags; }
    public String getTrustLevel() { return trustLevel; }
    public String getSource() { return source; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isGlobal() { return companyId == null; }
}
