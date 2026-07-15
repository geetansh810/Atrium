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
 * Attaches a knowledge doc to a role template so only that role's agents
 * retrieve it (14 §3) — e.g. only the content-writer role sees the brand
 * guide. Same composite-key shape as {@link RoleDefinitionSkill}, minus
 * {@code position}: recall ranks by cosine score, not attach order.
 */
@Entity
@Table(name = "role_definition_knowledge")
@IdClass(RoleDefinitionKnowledge.Key.class)
public class RoleDefinitionKnowledge {

    @Id
    @Column(name = "role_definition_id")
    private UUID roleDefinitionId;

    @Id
    @Column(name = "doc_id")
    private UUID docId;

    protected RoleDefinitionKnowledge() {}

    public RoleDefinitionKnowledge(UUID roleDefinitionId, UUID docId) {
        this.roleDefinitionId = roleDefinitionId;
        this.docId = docId;
    }

    public UUID getRoleDefinitionId() { return roleDefinitionId; }
    public UUID getDocId() { return docId; }

    public static class Key implements Serializable {
        private UUID roleDefinitionId;
        private UUID docId;

        public Key() {}

        public Key(UUID roleDefinitionId, UUID docId) {
            this.roleDefinitionId = roleDefinitionId;
            this.docId = docId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(roleDefinitionId, key.roleDefinitionId) && Objects.equals(docId, key.docId);
        }

        @Override
        public int hashCode() { return Objects.hash(roleDefinitionId, docId); }
    }
}
