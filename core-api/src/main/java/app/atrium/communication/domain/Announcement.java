package app.atrium.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A company-wide announcement (03 §V2). {@code category} is
 * company|update|maintenance.
 */
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String title;

    @Column
    private String body;

    @Column(nullable = false)
    private String category = "company";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Announcement() {}

    public Announcement(UUID companyId, String title, String body, String category) {
        this.companyId = companyId;
        this.title = title;
        this.body = body;
        this.category = category;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
}
