package app.atrium.routing.api;

import app.atrium.common.LogContext;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import app.atrium.registry.AgentDirectory;
import app.atrium.routing.WorkBroker;
import app.atrium.routing.api.TaskDtos.TaskResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Worker API gateway (12 §5, 16 §5) — the endpoints agent runtimes use,
 * authenticated as their agent via {@code X-Agent-Id} (04 rule 2; Phase 3:
 * per-company service token) — a separate auth axis from the human JWT (04
 * §Auth, M3.1): the tenant is resolved from the agent id itself
 * ({@link AgentDirectory#companyIdOf}), never from a bearer token, since
 * external agent runtimes have no human session. Claim 409 = someone else
 * holds it — workers must never retry the same claim. Fat-claim ContextBundle
 * lands whenever a real external (webhook/process) runtime needs it.
 *
 * <p>M3.2: binds {@link TenantContext#callAsSystem} around each call so Postgres
 * RLS (08 §Security rule 6) sees a real {@code app.company_id} on this axis
 * too — this gateway never goes through {@code TenantContextFilter}.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class WorkerTaskController {

    public static final String AGENT_HEADER = "X-Agent-Id";

    private final WorkBroker workBroker;
    private final AgentDirectory agentDirectory;

    public WorkerTaskController(WorkBroker workBroker, AgentDirectory agentDirectory) {
        this.workBroker = workBroker;
        this.agentDirectory = agentDirectory;
    }

    @PostMapping("/{id}/claim")
    public TaskResponse claim(@PathVariable UUID id,
                              @RequestHeader(AGENT_HEADER) UUID agentId) {
        UUID companyId = companyIdOf(agentId);
        LogContext.putAgent(agentId);
        LogContext.putTask(id);
        try {
            return TenantContext.callAsSystem(companyId,
                    () -> TaskResponse.from(workBroker.claim(companyId, id, agentId)));
        } finally {
            LogContext.clear();
        }
    }

    @PostMapping("/{id}/lease/renew")
    public TaskResponse renewLease(@PathVariable UUID id,
                                   @RequestHeader(AGENT_HEADER) UUID agentId) {
        UUID companyId = companyIdOf(agentId);
        LogContext.putAgent(agentId);
        LogContext.putTask(id);
        try {
            return TenantContext.callAsSystem(companyId,
                    () -> TaskResponse.from(workBroker.renewLease(companyId, id, agentId)));
        } finally {
            LogContext.clear();
        }
    }

    /**
     * Resolving an agent's own company by id is inherently pre-tenant (that's
     * the whole point) — bypasses RLS for this one lookup, same as
     * {@code AuthService}'s pre-auth email lookup.
     */
    private UUID companyIdOf(UUID agentId) {
        return TenantContext.callWithBypass(() ->
                agentDirectory.companyIdOf(agentId).orElseThrow(() -> NotFoundException.of("Agent", agentId)));
    }
}
