package app.atrium.registry.api;

import app.atrium.common.TenantContext;
import app.atrium.registry.RoleDefinitionService;
import app.atrium.registry.api.RoleDefinitionDtos.CreateRoleDefinitionRequest;
import app.atrium.registry.api.RoleDefinitionDtos.RoleDefinitionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/role-definitions")
public class RoleDefinitionController {

    private final RoleDefinitionService roleDefinitionService;

    public RoleDefinitionController(RoleDefinitionService roleDefinitionService) {
        this.roleDefinitionService = roleDefinitionService;
    }

    @GetMapping
    public List<RoleDefinitionResponse> list() {
        return roleDefinitionService.listVisible(TenantContext.requireCompanyId()).stream()
                .map(RoleDefinitionResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDefinitionResponse create(@Valid @RequestBody CreateRoleDefinitionRequest request) {
        return RoleDefinitionResponse.from(
                roleDefinitionService.createCustom(TenantContext.requireCompanyId(), request));
    }
}
