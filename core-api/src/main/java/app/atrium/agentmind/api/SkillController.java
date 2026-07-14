package app.atrium.agentmind.api;

import app.atrium.agentmind.SkillService;
import app.atrium.agentmind.api.SkillDtos.AddSkillVersionRequest;
import app.atrium.agentmind.api.SkillDtos.CreateSkillRequest;
import app.atrium.agentmind.api.SkillDtos.SkillResponse;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/api/v1/companies/{id}/skills")
    public List<SkillResponse> list(@PathVariable UUID id,
                                    @RequestParam(required = false) String kind,
                                    @RequestParam(required = false) String tag) {
        requireTenantMatch(id);
        return skillService.listVisible(id, kind, tag).stream().map(SkillResponse::from).toList();
    }

    @PostMapping("/api/v1/companies/{id}/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse create(@PathVariable UUID id, @Valid @RequestBody CreateSkillRequest request) {
        requireTenantMatch(id);
        return SkillResponse.from(skillService.create(id, request));
    }

    @PostMapping("/api/v1/skills/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse addVersion(@PathVariable UUID id, @Valid @RequestBody AddSkillVersionRequest request) {
        return SkillResponse.from(skillService.addVersion(TenantContext.requireCompanyId(), id, request));
    }

    @GetMapping("/api/v1/skills/{id}")
    public SkillResponse get(@PathVariable UUID id) {
        return SkillResponse.from(skillService.get(TenantContext.requireCompanyId(), id));
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
