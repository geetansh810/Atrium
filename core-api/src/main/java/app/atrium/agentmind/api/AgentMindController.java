package app.atrium.agentmind.api;

import app.atrium.agentmind.SkillService;
import app.atrium.agentmind.api.AgentMindDtos.AgentMindResponse;
import app.atrium.common.TenantContext;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** GET /agents/{id}/mind — the depth view (16 §1). Lives in agentmind, not registry, since it reads skills. */
@RestController
public class AgentMindController {

    private final SkillService skillService;

    public AgentMindController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/api/v1/agents/{id}/mind")
    public AgentMindResponse mind(@PathVariable UUID id) {
        return AgentMindResponse.from(skillService.mind(TenantContext.requireCompanyId(), id));
    }
}
