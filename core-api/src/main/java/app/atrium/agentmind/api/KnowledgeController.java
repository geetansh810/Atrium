package app.atrium.agentmind.api;

import app.atrium.agentmind.KnowledgeService;
import app.atrium.agentmind.api.KnowledgeDtos.IngestKnowledgeRequest;
import app.atrium.agentmind.api.KnowledgeDtos.KnowledgeDocResponse;
import app.atrium.common.NotFoundException;
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

/** Knowledge ingest/list/archive (16 §3). */
@RestController
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/api/v1/companies/{id}/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocResponse ingest(@PathVariable UUID id, @Valid @RequestBody IngestKnowledgeRequest request) {
        requireTenantMatch(id);
        return KnowledgeDocResponse.from(knowledgeService.ingest(id, request));
    }

    @GetMapping("/api/v1/companies/{id}/knowledge")
    public List<KnowledgeDocResponse> list(@PathVariable UUID id, @RequestParam(required = false) String status) {
        requireTenantMatch(id);
        return knowledgeService.list(id, status).stream().map(KnowledgeDocResponse::from).toList();
    }

    @DeleteMapping("/api/v1/knowledge/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        knowledgeService.archive(TenantContext.requireCompanyId(), id);
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
