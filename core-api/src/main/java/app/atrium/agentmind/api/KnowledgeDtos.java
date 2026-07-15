package app.atrium.agentmind.api;

import app.atrium.agentmind.domain.KnowledgeDoc;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

public final class KnowledgeDtos {

    private KnowledgeDtos() {}

    /** 16 §3: {title, content} → doc (v1 accepts text/markdown only, 14 §3). */
    public record IngestKnowledgeRequest(
            @NotBlank String title,
            @NotBlank String content,
            @Nullable String sourceUri) {}

    public record AttachRoleKnowledgeRequest(@NotNull UUID docId) {}

    public record KnowledgeDocResponse(
            UUID id, UUID companyId, String title, @Nullable String sourceUri, String mime, String status,
            Instant createdAt) {

        public static KnowledgeDocResponse from(KnowledgeDoc d) {
            return new KnowledgeDocResponse(d.getId(), d.getCompanyId(), d.getTitle(), d.getSourceUri(),
                    d.getMime(), d.getStatus(), d.getCreatedAt());
        }
    }
}
