package app.atrium.execution;

import app.atrium.common.FieldValidationException;
import app.atrium.common.KeysetCursors;
import app.atrium.execution.domain.LlmRequestLog;
import app.atrium.execution.domain.LlmRequestLogRepository;
import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists and lists the LLM request log (V14). Writes happen at {@code
 * LlmRouter.complete} (the single provider doorway) in their own
 * REQUIRES_NEW transaction so the log commits regardless of what the calling
 * attempt does next, and a logging failure is swallowed — it must never break
 * the LLM call it describes.
 */
@Service
public class LlmRequestLogService {

    private static final Logger log = LoggerFactory.getLogger(LlmRequestLogService.class);

    private static final List<String> STATUSES = List.of("ok", "error");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final LlmRequestLogRepository repo;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public LlmRequestLogService(LlmRequestLogRepository repo, EntityManager entityManager,
                                ObjectMapper objectMapper) {
        this.repo = repo;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Record one LLM call, best-effort. Exactly one of {@code result}/{@code error}
     * is non-null. Runs in its own transaction (REQUIRES_NEW) and never
     * propagates — the caller's LLM call succeeds or fails on its own merits,
     * regardless of whether this row lands.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID companyId, @Nullable UUID agentId, @Nullable UUID taskId,
                       LlmRequest request, @Nullable LlmResult result,
                       @Nullable LlmException error, long latencyMs) {
        try {
            LlmRequestLog.Builder b = LlmRequestLog.builder()
                    .companyId(companyId)
                    .agentId(agentId)
                    .taskId(taskId)
                    .provider(request.provider())
                    .model(request.model())
                    .systemPrompt(request.system())
                    .messages(objectMapper.valueToTree(request.messages()))
                    .latencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            if (request.tools() != null && !request.tools().isEmpty()) {
                b.tools(objectMapper.valueToTree(request.tools()));
            }
            if (result != null) {
                b.status("ok")
                        .responseText(result.content())
                        .tokensIn(result.tokensIn())
                        .tokensOut(result.tokensOut())
                        .stopReason(result.stopReason())
                        .providerRequestId(result.providerRequestId());
                if (result.toolCalls() != null && !result.toolCalls().isEmpty()) {
                    b.toolCalls(objectMapper.valueToTree(result.toolCalls()));
                }
            } else {
                b.status("error")
                        .errorKind(error != null ? error.kind().name() : "UNKNOWN")
                        .errorMessage(error != null ? error.getMessage() : null);
            }
            repo.save(b.build());
        } catch (RuntimeException e) {
            // Never let a logging failure surface into the LLM call path.
            log.warn("Failed to record LLM request log (company={}, provider={}, model={})",
                    companyId, request.provider(), request.model(), e);
        }
    }

    /** Newest-first, keyset-paginated (04 rule 3), company-scoped with optional filters. */
    @Transactional(readOnly = true)
    public LogPage list(UUID companyId, LogListQuery query) {
        int limit = clampLimit(query.limit());

        StringBuilder sql = new StringBuilder(
                "SELECT * FROM llm_request_logs WHERE company_id = :companyId");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId);

        if (query.agentId() != null) {
            sql.append(" AND agent_id = :agentId");
            params.put("agentId", query.agentId());
        }
        if (query.taskId() != null) {
            sql.append(" AND task_id = :taskId");
            params.put("taskId", query.taskId());
        }
        if (query.status() != null) {
            if (!STATUSES.contains(query.status())) {
                throw new FieldValidationException(Map.of("status", "must be one of " + STATUSES));
            }
            sql.append(" AND status = :status");
            params.put("status", query.status());
        }
        if (query.provider() != null) {
            sql.append(" AND provider = :provider");
            params.put("provider", query.provider());
        }
        if (query.cursor() != null) {
            KeysetCursors.Position position = KeysetCursors.decode(query.cursor());
            sql.append(" AND (created_at, id) < (:cursorCreatedAt, :cursorId)");
            params.put("cursorCreatedAt", position.createdAt());
            params.put("cursorId", position.id());
        }
        sql.append(" ORDER BY created_at DESC, id DESC");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), LlmRequestLog.class);
        params.forEach(nativeQuery::setParameter);
        nativeQuery.setMaxResults(limit + 1);

        @SuppressWarnings("unchecked")
        List<LlmRequestLog> page = nativeQuery.getResultList();
        String nextCursor = null;
        if (page.size() > limit) {
            page = page.subList(0, limit);
            LlmRequestLog last = page.get(limit - 1);
            nextCursor = KeysetCursors.encode(last.getCreatedAt(), last.getId());
        }
        return new LogPage(page, nextCursor);
    }

    @Transactional(readOnly = true)
    public LlmRequestLog get(UUID companyId, UUID logId) {
        return repo.findById(logId)
                .filter(row -> row.getCompanyId().equals(companyId))
                .orElseThrow(() -> app.atrium.common.NotFoundException.of("LlmRequestLog", logId));
    }

    private int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(requested, MAX_LIMIT));
    }

    public record LogListQuery(@Nullable UUID agentId, @Nullable UUID taskId,
                               @Nullable String status, @Nullable String provider,
                               @Nullable String cursor, @Nullable Integer limit) {}

    public record LogPage(List<LlmRequestLog> data, @Nullable String nextCursor) {}
}
