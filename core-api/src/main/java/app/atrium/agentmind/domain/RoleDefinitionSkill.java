package app.atrium.agentmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Every agent hired from a role template inherits this attachment set
 * (14 §1). skill_id pins a specific version — a role upgrade to a newer
 * skill version is an explicit re-attach, never automatic.
 */
@Entity
@Table(name = "role_definition_skills")
@IdClass(RoleDefinitionSkill.Key.class)
public class RoleDefinitionSkill {

    @Id
    @Column(name = "role_definition_id")
    private UUID roleDefinitionId;

    @Id
    @Column(name = "skill_id")
    private UUID skillId;

    @Column(nullable = false)
    private int position = 0;

    protected RoleDefinitionSkill() {}

    public RoleDefinitionSkill(UUID roleDefinitionId, UUID skillId, int position) {
        this.roleDefinitionId = roleDefinitionId;
        this.skillId = skillId;
        this.position = position;
    }

    public UUID getRoleDefinitionId() { return roleDefinitionId; }
    public UUID getSkillId() { return skillId; }
    public int getPosition() { return position; }

    public static class Key implements Serializable {
        private UUID roleDefinitionId;
        private UUID skillId;

        public Key() {}

        public Key(UUID roleDefinitionId, UUID skillId) {
            this.roleDefinitionId = roleDefinitionId;
            this.skillId = skillId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(roleDefinitionId, key.roleDefinitionId)
                    && Objects.equals(skillId, key.skillId);
        }

        @Override
        public int hashCode() { return Objects.hash(roleDefinitionId, skillId); }
    }
}
