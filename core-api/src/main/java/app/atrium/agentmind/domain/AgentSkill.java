package app.atrium.agentmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A per-agent extra beyond its role's attached set (14 §1). */
@Entity
@Table(name = "agent_skills")
@IdClass(AgentSkill.Key.class)
public class AgentSkill {

    @Id
    @Column(name = "agent_id")
    private UUID agentId;

    @Id
    @Column(name = "skill_id")
    private UUID skillId;

    /** hired | assigned | learned. */
    @Column(nullable = false)
    private String source = "assigned";

    @Column(nullable = false)
    private short proficiency = 3;

    @Column(name = "attached_at", nullable = false, updatable = false)
    private Instant attachedAt = Instant.now();

    protected AgentSkill() {}

    public AgentSkill(UUID agentId, UUID skillId, String source, short proficiency) {
        this.agentId = agentId;
        this.skillId = skillId;
        this.source = source;
        this.proficiency = proficiency;
    }

    public UUID getAgentId() { return agentId; }
    public UUID getSkillId() { return skillId; }
    public String getSource() { return source; }
    public short getProficiency() { return proficiency; }
    public Instant getAttachedAt() { return attachedAt; }

    public void setProficiency(short proficiency) { this.proficiency = proficiency; }

    public static class Key implements Serializable {
        private UUID agentId;
        private UUID skillId;

        public Key() {}

        public Key(UUID agentId, UUID skillId) {
            this.agentId = agentId;
            this.skillId = skillId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(agentId, key.agentId) && Objects.equals(skillId, key.skillId);
        }

        @Override
        public int hashCode() { return Objects.hash(agentId, skillId); }
    }
}
