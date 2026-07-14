package app.atrium.agentmind.api;

import app.atrium.agentmind.SkillService;
import app.atrium.agentmind.api.SkillDtos.AttachAgentSkillRequest;
import app.atrium.common.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/skills")
public class AgentSkillController {

    private final SkillService skillService;

    public AgentSkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attach(@PathVariable UUID agentId, @Valid @RequestBody AttachAgentSkillRequest request) {
        skillService.attachToAgent(TenantContext.requireCompanyId(), agentId, request.skillId(),
                request.proficiency());
    }

    @DeleteMapping("/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(@PathVariable UUID agentId, @PathVariable UUID skillId) {
        skillService.detachFromAgent(TenantContext.requireCompanyId(), agentId, skillId);
    }
}
