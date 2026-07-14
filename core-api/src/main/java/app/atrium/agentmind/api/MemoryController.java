package app.atrium.agentmind.api;

import app.atrium.agentmind.MemoryService;
import app.atrium.agentmind.api.MemoryDtos.MemoryBrowseQuery;
import app.atrium.agentmind.api.MemoryDtos.MemoryResponse;
import app.atrium.agentmind.api.MemoryDtos.ReviewMemoryRequest;
import app.atrium.agentmind.api.MemoryDtos.SeedMemoryRequest;
import app.atrium.common.NotFoundException;
import app.atrium.common.PageEnvelope;
import app.atrium.common.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Memory browse/seed/forget/review-queue/review (16 §3). */
@RestController
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/api/v1/companies/{id}/memories")
    public PageEnvelope<MemoryResponse> browse(@PathVariable UUID id,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        requireTenantMatch(id);
        MemoryService.MemoryPage page = memoryService.browse(id,
                new MemoryBrowseQuery(scope, agentId, kind, status, q, limit, cursor));
        return new PageEnvelope<>(page.data().stream().map(MemoryResponse::from).toList(), page.nextCursor());
    }

    @PostMapping("/api/v1/companies/{id}/memories")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse seed(@PathVariable UUID id, @Valid @RequestBody SeedMemoryRequest request) {
        requireTenantMatch(id);
        return MemoryResponse.from(memoryService.seed(id, request));
    }

    @DeleteMapping("/api/v1/memories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forget(@PathVariable UUID id) {
        memoryService.forget(TenantContext.requireCompanyId(), id);
    }

    @GetMapping("/api/v1/companies/{id}/memories/review-queue")
    public PageEnvelope<MemoryResponse> reviewQueue(@PathVariable UUID id) {
        requireTenantMatch(id);
        List<MemoryResponse> data = memoryService.reviewQueue(id).stream().map(MemoryResponse::from).toList();
        return new PageEnvelope<>(data, null);
    }

    @PostMapping("/api/v1/memories/{id}/review")
    public MemoryResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewMemoryRequest request) {
        return MemoryResponse.from(memoryService.review(TenantContext.requireCompanyId(), id, request));
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
