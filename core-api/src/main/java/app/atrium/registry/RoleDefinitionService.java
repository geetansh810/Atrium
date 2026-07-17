package app.atrium.registry;

import app.atrium.registry.api.RoleDefinitionDtos.CreateRoleDefinitionRequest;
import app.atrium.registry.domain.RoleDefinition;
import app.atrium.registry.domain.RoleDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleDefinitionService {

    private final RoleDefinitionRepository roleDefinitions;
    private final ObjectMapper objectMapper;

    public RoleDefinitionService(RoleDefinitionRepository roleDefinitions, ObjectMapper objectMapper) {
        this.roleDefinitions = roleDefinitions;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RoleDefinition> listVisible(UUID companyId) {
        return roleDefinitions.findVisibleToCompany(companyId);
    }

    /** New custom definition; same key bumps the version (roles are versioned data). */
    @Transactional
    public RoleDefinition createCustom(UUID companyId, CreateRoleDefinitionRequest request) {
        int nextVersion = roleDefinitions
                .findFirstByCompanyIdAndKeyOrderByVersionDesc(companyId, request.key())
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
        RoleDefinition roleDefinition = new RoleDefinition(companyId, request.key(), nextVersion,
                request.title(), request.systemPrompt(),
                request.allowedTools() != null ? request.allowedTools() : objectMapper.createArrayNode(),
                request.outputContract(),
                request.reviewRequired() != null && request.reviewRequired());
        return roleDefinitions.save(roleDefinition);
    }
}
