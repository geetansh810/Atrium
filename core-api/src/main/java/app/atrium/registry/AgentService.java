package app.atrium.registry;

import app.atrium.common.FieldValidationException;
import app.atrium.common.NotFoundException;
import app.atrium.registry.api.AgentDtos.HireAgentRequest;
import app.atrium.registry.api.AgentDtos.PatchAgentRequest;
import app.atrium.registry.domain.Agent;
import app.atrium.registry.domain.AgentRepository;
import app.atrium.registry.domain.RoleDefinition;
import app.atrium.registry.domain.RoleDefinitionRepository;
import app.atrium.registry.runtime.RuntimeRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {

    private static final Set<String> PRESENCE_STATUSES =
            Set.of("online", "working", "in_meeting", "in_focus", "away", "offline");

    private final AgentRepository agents;
    private final RoleDefinitionRepository roleDefinitions;
    private final RuntimeRegistry runtimeRegistry;
    private final ObjectMapper objectMapper;

    public AgentService(AgentRepository agents, RoleDefinitionRepository roleDefinitions,
                        RuntimeRegistry runtimeRegistry, ObjectMapper objectMapper) {
        this.agents = agents;
        this.roleDefinitions = roleDefinitions;
        this.runtimeRegistry = runtimeRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Agent hire(UUID companyId, HireAgentRequest request) {
        RoleDefinition roleDefinition = resolveRole(companyId, request);

        String runtimeType = request.runtimeType() != null ? request.runtimeType() : "llm_loop";
        JsonNode runtimeConfig = request.runtimeConfig() != null
                ? request.runtimeConfig() : objectMapper.createObjectNode();
        runtimeRegistry.validate(runtimeType, runtimeConfig);

        if (request.managerAgentId() != null) {
            validateManagerChain(companyId, null, request.managerAgentId());
        }

        Agent agent = new Agent(companyId, request.name(),
                request.spriteKey() != null ? request.spriteKey() : "adam",
                roleDefinition.getId(), request.roleTitle(), request.skillTags(),
                request.modelProvider(), request.modelName(), request.managerAgentId(),
                request.about(), runtimeType, runtimeConfig);
        return agents.save(agent);
    }

    @Transactional
    public Agent patch(UUID companyId, UUID agentId, PatchAgentRequest request) {
        Agent agent = agents.findByIdAndCompanyId(agentId, companyId)
                .orElseThrow(() -> NotFoundException.of("Agent", agentId));

        if (request.runtimeType() != null || request.runtimeConfig() != null) {
            String runtimeType = request.runtimeType() != null
                    ? request.runtimeType() : agent.getRuntimeType();
            JsonNode runtimeConfig = request.runtimeConfig() != null
                    ? request.runtimeConfig() : agent.getRuntimeConfig();
            runtimeRegistry.validate(runtimeType, runtimeConfig);
            agent.setRuntimeType(runtimeType);
            agent.setRuntimeConfig(runtimeConfig);
        }
        if (request.managerAgentId() != null) {
            validateManagerChain(companyId, agentId, request.managerAgentId());
            agent.setManagerAgentId(request.managerAgentId());
        }
        if (request.status() != null) {
            if (!PRESENCE_STATUSES.contains(request.status())) {
                throw new FieldValidationException(
                        Map.of("status", "must be one of " + PRESENCE_STATUSES));
            }
            agent.setStatus(request.status());
        }
        if (request.name() != null) agent.setName(request.name());
        if (request.spriteKey() != null) agent.setSpriteKey(request.spriteKey());
        if (request.roleTitle() != null) agent.setRoleTitle(request.roleTitle());
        if (request.skillTags() != null) {
            if (request.skillTags().isEmpty()) {
                throw new FieldValidationException(Map.of("skillTags", "must not be empty"));
            }
            agent.setSkillTags(request.skillTags());
        }
        if (request.modelProvider() != null) agent.setModelProvider(request.modelProvider());
        if (request.modelName() != null) agent.setModelName(request.modelName());
        if (request.about() != null) agent.setAbout(request.about());
        if (request.paused() != null) {
            // Runtime start/stop side effects arrive with AgentLifecycleService (M0.5b)
            agent.setPaused(request.paused());
        }
        return agent;
    }

    @Transactional(readOnly = true)
    public List<Agent> roster(UUID companyId) {
        return agents.findByCompanyIdOrderByJoinedAt(companyId);
    }

    @Transactional(readOnly = true)
    public Agent get(UUID companyId, UUID agentId) {
        return agents.findByIdAndCompanyId(agentId, companyId)
                .orElseThrow(() -> NotFoundException.of("Agent", agentId));
    }

    private RoleDefinition resolveRole(UUID companyId, HireAgentRequest request) {
        boolean hasId = request.roleDefinitionId() != null;
        boolean hasTemplateKey = request.roleTemplateKey() != null;
        if (hasId == hasTemplateKey) {
            throw new FieldValidationException(Map.of(
                    "roleDefinitionId", "exactly one of roleDefinitionId or roleTemplateKey is required",
                    "roleTemplateKey", "exactly one of roleDefinitionId or roleTemplateKey is required"));
        }
        if (hasTemplateKey) {
            return roleDefinitions
                    .findFirstByCompanyIdIsNullAndKeyOrderByVersionDesc(request.roleTemplateKey())
                    .orElseThrow(() -> NotFoundException.of("Role template", request.roleTemplateKey()));
        }
        return roleDefinitions.findByIdVisibleToCompany(request.roleDefinitionId(), companyId)
                .orElseThrow(() -> NotFoundException.of("Role definition", request.roleDefinitionId()));
    }

    /**
     * Walk up the manager chain with a visited set (05 §registry: no cycles,
     * validate on write). agentId is null when hiring — a brand-new agent can't
     * be in anyone's chain yet, but the manager must still exist in-company.
     */
    private void validateManagerChain(UUID companyId, UUID agentId, UUID managerId) {
        if (managerId.equals(agentId)) {
            throw new FieldValidationException(Map.of("managerAgentId", "an agent cannot manage itself"));
        }
        Set<UUID> visited = new HashSet<>();
        UUID current = managerId;
        while (current != null) {
            if (!visited.add(current)) {
                throw new FieldValidationException(
                        Map.of("managerAgentId", "manager chain already contains a cycle"));
            }
            Agent manager = agents.findByIdAndCompanyId(current, companyId)
                    .orElseThrow(() -> NotFoundException.of("Manager agent", managerId));
            if (manager.getId().equals(agentId)) {
                throw new FieldValidationException(
                        Map.of("managerAgentId", "would create a manager cycle"));
            }
            current = manager.getManagerAgentId();
        }
    }
}
