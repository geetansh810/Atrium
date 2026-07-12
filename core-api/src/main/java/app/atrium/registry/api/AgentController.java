package app.atrium.registry.api;

import app.atrium.common.TenantContext;
import app.atrium.registry.AgentService;
import app.atrium.registry.api.AgentDtos.AgentProfileResponse;
import app.atrium.registry.api.AgentDtos.AgentResponse;
import app.atrium.registry.api.AgentDtos.PatchAgentRequest;
import app.atrium.registry.api.AgentDtos.ProfileStats;
import app.atrium.registry.domain.Agent;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PatchMapping("/{id}")
    public AgentResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchAgentRequest request) {
        return AgentResponse.from(agentService.patch(TenantContext.requireCompanyId(), id, request));
    }

    @GetMapping("/{id}/profile")
    public AgentProfileResponse profile(@PathVariable UUID id) {
        Agent agent = agentService.get(TenantContext.requireCompanyId(), id);
        // stats/currentTasks/activityFeed populate at M0.3 (tasks) and M2.3 (rollups)
        return new AgentProfileResponse(AgentResponse.from(agent), ProfileStats.zero(),
                agent.getSkillTags(), List.of(), List.of());
    }
}
