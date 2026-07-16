package app.atrium.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A chat channel (03 §V2). {@code kind='channel'} for shared rooms
 * ('general','announcements',…), {@code 'dm'} for a 1:1 with one agent or the
 * bot. Unique per (company, name).
 */
@Entity
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String kind = "channel";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Channel() {}

    public Channel(UUID companyId, String name, String kind) {
        this.companyId = companyId;
        this.name = name;
        this.kind = kind;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public Instant getCreatedAt() { return createdAt; }
}
