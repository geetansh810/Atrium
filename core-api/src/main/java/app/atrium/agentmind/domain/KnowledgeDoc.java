package app.atrium.agentmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * A company-scoped reference document (14 §3, table per 15 §4.3), chunked +
 * embedded into {@code knowledge_chunks} by {@link
 * app.atrium.agentmind.KnowledgeService#ingest}. Unlike {@link Skill}, there
 * are no global/platform docs — every row is company-owned.
 */
@Entity
@Table(name = "knowledge_docs")
public class KnowledgeDoc {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_uri")
    private String sourceUri;

    @Column(nullable = false)
    private String mime = "text/markdown";

    /** ingesting | active | archived | failed (15 §4.3 CHECK) — v1 only ever writes active/archived (see KnowledgeService). */
    @Column(nullable = false)
    private String status = "active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected KnowledgeDoc() {}

    public KnowledgeDoc(UUID companyId, String title, @Nullable String sourceUri, String mime, String status) {
        this.companyId = companyId;
        this.title = title;
        this.sourceUri = sourceUri;
        this.mime = mime;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getTitle() { return title; }
    @Nullable public String getSourceUri() { return sourceUri; }
    public String getMime() { return mime; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
}
