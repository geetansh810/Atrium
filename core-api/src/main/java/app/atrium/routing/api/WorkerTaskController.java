package app.atrium.routing.api;

import app.atrium.common.TenantContext;
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
 * per-company service token). Claim 409 = someone else holds it — workers
 * must never retry the same claim. Fat-claim ContextBundle lands at M-AR1.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class WorkerTaskController {

    public static final String AGENT_HEADER = "X-Agent-Id";

    private final WorkBroker workBroker;

    public WorkerTaskController(WorkBroker workBroker) {
        this.workBroker = workBroker;
    }

    @PostMapping("/{id}/claim")
    public TaskResponse claim(@PathVariable UUID id,
                              @RequestHeader(AGENT_HEADER) UUID agentId) {
        return TaskResponse.from(
                workBroker.claim(TenantContext.requireCompanyId(), id, agentId));
    }

    @PostMapping("/{id}/lease/renew")
    public TaskResponse renewLease(@PathVariable UUID id,
                                   @RequestHeader(AGENT_HEADER) UUID agentId) {
        return TaskResponse.from(
                workBroker.renewLease(TenantContext.requireCompanyId(), id, agentId));
    }
}
