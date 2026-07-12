package app.atrium.registry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Human member. Minimal until Phase 3 auth — dev mode identifies users by X-User-Id header. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String role = "admin";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(UUID companyId, String displayName, String email) {
        this.companyId = companyId;
        this.displayName = displayName;
        this.email = email;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
