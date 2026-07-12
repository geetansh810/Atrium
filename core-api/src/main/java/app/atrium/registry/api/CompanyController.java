package app.atrium.registry.api;

import app.atrium.common.TenantContext;
import app.atrium.registry.AgentService;
import app.atrium.registry.CompanyService;
import app.atrium.registry.api.AgentDtos.AgentResponse;
import app.atrium.registry.api.AgentDtos.HireAgentRequest;
import app.atrium.registry.api.CompanyDtos.CompanyResponse;
import app.atrium.registry.api.CompanyDtos.CreateCompanyRequest;
import app.atrium.common.NotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final AgentService agentService;

    public CompanyController(CompanyService companyService, AgentService agentService) {
        this.companyService = companyService;
        this.agentService = agentService;
    }

    /** Bootstrap call — the one /api route exempt from the X-Company-Id requirement. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CreateCompanyRequest request) {
        return CompanyResponse.from(companyService.create(request));
    }

    @GetMapping("/{id}")
    public CompanyResponse get(@PathVariable UUID id) {
        return CompanyResponse.from(companyService.get(TenantContext.requireCompanyId(), id));
    }

    @PostMapping("/{id}/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse hire(@PathVariable UUID id, @Valid @RequestBody HireAgentRequest request) {
        requireTenantMatch(id);
        return AgentResponse.from(agentService.hire(id, request));
    }

    @GetMapping("/{id}/roster")
    public List<AgentResponse> roster(@PathVariable UUID id) {
        requireTenantMatch(id);
        return agentService.roster(id).stream().map(AgentResponse::from).toList();
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
