package app.atrium.agentmind.api;

import app.atrium.agentmind.SkillService;
import app.atrium.agentmind.api.SkillDtos.AttachRoleSkillRequest;
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

/** Attach a skill to a role template — affects future hires only (16 §2). */
@RestController
@RequestMapping("/api/v1/role-definitions/{roleDefinitionId}/skills")
public class RoleSkillController {

    private final SkillService skillService;

    public RoleSkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attach(@PathVariable UUID roleDefinitionId, @Valid @RequestBody AttachRoleSkillRequest request) {
        skillService.attachToRole(TenantContext.requireCompanyId(), roleDefinitionId, request.skillId(),
                request.position());
    }
}
