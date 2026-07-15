package app.atrium.agentmind.api;

import app.atrium.agentmind.KnowledgeService;
import app.atrium.agentmind.api.KnowledgeDtos.AttachRoleKnowledgeRequest;
import app.atrium.common.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Attach a knowledge doc to a role template — affects future recall only, respected per-role (16 §3). */
@RestController
@RequestMapping("/api/v1/role-definitions/{roleDefinitionId}/knowledge")
public class RoleKnowledgeController {

    private final KnowledgeService knowledgeService;

    public RoleKnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attach(@PathVariable UUID roleDefinitionId, @Valid @RequestBody AttachRoleKnowledgeRequest request) {
        knowledgeService.attachToRole(TenantContext.requireCompanyId(), roleDefinitionId, request.docId());
    }
}
