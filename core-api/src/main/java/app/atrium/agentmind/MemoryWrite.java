package app.atrium.agentmind;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Input to {@link MemoryStore#ingest} (14 §2). {@code scope} decides which of
 * {@code agentId}/{@code roleKey}/{@code taskId} is required (15 §4.2 CHECK
 * constraints — validated in {@link MemoryService} before this reaches the store).
 * {@code provenance} is required (Paperclip rule): {@code {taskId?, eventId?,
 * artifactId?, extractedBy:'pipeline'|'user', modelUsed?}}.
 */
public record MemoryWrite(UUID companyId, String scope, @Nullable UUID agentId, @Nullable String roleKey,
                          @Nullable UUID taskId, String kind, String content, short importance,
                          String status, JsonNode provenance, @Nullable Long sourceEventId) {}
