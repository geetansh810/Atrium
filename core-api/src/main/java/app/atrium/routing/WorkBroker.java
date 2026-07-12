package app.atrium.routing;

import app.atrium.routing.domain.Task;
import java.util.UUID;

/**
 * The claim/lease surface of routing (05 §routing) — v1 impl is Postgres
 * ({@code FOR UPDATE SKIP LOCKED}). Workers (external runtimes via the Worker
 * API gateway, internal runners at M0.5b) touch tasks only through this.
 *
 * <p>Worker rule (16 §5): a 409 from claim means someone else holds the task —
 * <b>never retry the same claim</b>; move on.
 */
public interface WorkBroker {

    /**
     * Claim the named task for the agent — the canonical claim query (03,
     * M0.4-amended: {@code attempt++}, paused-agent guard) keyed by task id,
     * plus {@link app.atrium.accountability.BudgetGuard} in the same tx.
     * 409 if held/not-queued/paused/over-budget; the body says who holds it.
     */
    Task claim(UUID companyId, UUID taskId, UUID agentId);

    /** Heartbeat: extend the lease 10 minutes. 409 unless the agent holds the task. */
    Task renewLease(UUID companyId, UUID taskId, UUID agentId);
}
