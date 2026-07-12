package app.atrium.registry.domain;

import com.fasterxml.jackson.databind.JsonNode;
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

@Entity
@Table(name = "agents")
public class Agent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "sprite_key", nullable = false)
    private String spriteKey = "adam";

    @Column(name = "role_definition_id", nullable = false)
    private UUID roleDefinitionId;

    @Column(name = "role_title", nullable = false)
    private String roleTitle;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skill_tags", nullable = false, columnDefinition = "text[]")
    private List<String> skillTags;

    @Column(name = "model_provider", nullable = false)
    private String modelProvider;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "manager_agent_id")
    private UUID managerAgentId;

    /** Presence in the office: online/working/in_meeting/in_focus/away/offline. */
    @Column(nullable = false)
    private String status = "offline";

    private String about;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    /** Validated against RuntimeRegistry, deliberately no DB CHECK (13 §3.1). */
    @Column(name = "runtime_type", nullable = false)
    private String runtimeType = "llm_loop";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime_config", nullable = false)
    private JsonNode runtimeConfig;

    /** Board/budget pause — distinct from status, which is presence. */
    @Column(nullable = false)
    private boolean paused = false;

    protected Agent() {}

    public Agent(UUID companyId, String name, String spriteKey, UUID roleDefinitionId,
                 String roleTitle, List<String> skillTags, String modelProvider, String modelName,
                 UUID managerAgentId, String about, String runtimeType, JsonNode runtimeConfig) {
        this.companyId = companyId;
        this.name = name;
        this.spriteKey = spriteKey;
        this.roleDefinitionId = roleDefinitionId;
        this.roleTitle = roleTitle;
        this.skillTags = skillTags;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.managerAgentId = managerAgentId;
        this.about = about;
        this.runtimeType = runtimeType;
        this.runtimeConfig = runtimeConfig;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public String getSpriteKey() { return spriteKey; }
    public UUID getRoleDefinitionId() { return roleDefinitionId; }
    public String getRoleTitle() { return roleTitle; }
    public List<String> getSkillTags() { return skillTags; }
    public String getModelProvider() { return modelProvider; }
    public String getModelName() { return modelName; }
    public UUID getManagerAgentId() { return managerAgentId; }
    public String getStatus() { return status; }
    public String getAbout() { return about; }
    public Instant getJoinedAt() { return joinedAt; }
    public String getRuntimeType() { return runtimeType; }
    public JsonNode getRuntimeConfig() { return runtimeConfig; }
    public boolean isPaused() { return paused; }

    public void setName(String name) { this.name = name; }
    public void setSpriteKey(String spriteKey) { this.spriteKey = spriteKey; }
    public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
    public void setSkillTags(List<String> skillTags) { this.skillTags = skillTags; }
    public void setModelProvider(String modelProvider) { this.modelProvider = modelProvider; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public void setManagerAgentId(UUID managerAgentId) { this.managerAgentId = managerAgentId; }
    public void setStatus(String status) { this.status = status; }
    public void setAbout(String about) { this.about = about; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public void setRuntimeConfig(JsonNode runtimeConfig) { this.runtimeConfig = runtimeConfig; }
    public void setPaused(boolean paused) { this.paused = paused; }
}
