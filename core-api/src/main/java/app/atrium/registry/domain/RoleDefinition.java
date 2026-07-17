package app.atrium.registry.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** What makes an agent "deep", versioned. company_id NULL = global template. */
@Entity
@Table(name = "role_definitions")
public class RoleDefinition {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private String title;

    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_tools", nullable = false)
    private JsonNode allowedTools;

    @Column(name = "output_contract")
    private String outputContract;

    /** M4.1 (07 Phase 4) — true structurally blocks any task assigned to an
     *  agent hired against this role from reaching 'approved' except via a
     *  named human user; see {@code TaskService.approve}. */
    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    protected RoleDefinition() {}

    public RoleDefinition(UUID companyId, String key, int version, String title,
                          String systemPrompt, JsonNode allowedTools, String outputContract,
                          boolean reviewRequired) {
        this.companyId = companyId;
        this.key = key;
        this.version = version;
        this.title = title;
        this.systemPrompt = systemPrompt;
        this.allowedTools = allowedTools;
        this.outputContract = outputContract;
        this.reviewRequired = reviewRequired;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getKey() { return key; }
    public int getVersion() { return version; }
    public String getTitle() { return title; }
    public String getSystemPrompt() { return systemPrompt; }
    public JsonNode getAllowedTools() { return allowedTools; }
    public String getOutputContract() { return outputContract; }
    public boolean isReviewRequired() { return reviewRequired; }

    public boolean isGlobalTemplate() { return companyId == null; }
}
