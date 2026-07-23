package app.atrium.execution.api;

import app.atrium.common.NotFoundException;
import app.atrium.common.PageEnvelope;
import app.atrium.common.TenantContext;
import app.atrium.execution.LlmRequestLogService;
import app.atrium.execution.LlmRequestLogService.LogListQuery;
import app.atrium.execution.LlmRequestLogService.LogPage;
import app.atrium.execution.api.LlmRequestLogDtos.LogDetail;
import app.atrium.execution.api.LlmRequestLogDtos.LogRow;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only LLM request log (V14): every call sent to a provider, with the
 * full prompt and response, company-scoped and newest-first. Populated
 * automatically at {@code LlmRouter.complete}; nothing writes here via HTTP.
 */
@RestController
@RequestMapping("/api/v1/companies/{id}/llm-logs")
public class LlmRequestLogController {

    private final LlmRequestLogService service;

    public LlmRequestLogController(LlmRequestLogService service) {
        this.service = service;
    }

    @GetMapping
    public PageEnvelope<LogRow> list(@PathVariable UUID id,
                                     @RequestParam(required = false) UUID agentId,
                                     @RequestParam(required = false) UUID taskId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String provider,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer limit) {
        requireTenantMatch(id);
        LogPage page = service.list(id, new LogListQuery(agentId, taskId, status, provider, cursor, limit));
        return new PageEnvelope<>(page.data().stream().map(LogRow::from).toList(), page.nextCursor());
    }

    @GetMapping("/{logId}")
    public LogDetail get(@PathVariable UUID id, @PathVariable UUID logId) {
        requireTenantMatch(id);
        return LogDetail.from(service.get(id, logId));
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
