package app.atrium.routing;

import app.atrium.routing.domain.Task;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.lang.Nullable;

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

    /**
     * The runner's claim-next (M0.5b, 03 §claim note): skill-ordered variant of
     * the canonical claim query — same SET list, status/paused guards and
     * {@code FOR UPDATE SKIP LOCKED}, but the inner SELECT matches any of the
     * agent's skill tags instead of one named task. Empty = nothing queued for
     * this agent right now (not an error — the loop just continues).
     *
     * @param claimedPayloadEnricher optional hook run on the just-claimed task,
     *        INSIDE the same transaction as the claim and its {@code claimed}
     *        audit event (03 invariant 1 — state change and event must be
     *        same-tx) — its returned fields are merged into that event's
     *        payload. Kept as a plain JDK {@link Function} rather than a named
     *        interface so routing never has to import the caller's types
     *        (M-CTX1: execution passes a lambda that assembles context and
     *        returns {@code {contextProvenance: [...]}}). Local-DB-only work,
     *        please — this runs before any LLM call and holds the claim's
     *        row lock a little longer.
     */
    Optional<Task> claimNext(UUID companyId, UUID agentId, List<String> skillTags,
                              @Nullable Function<Task, ObjectNode> claimedPayloadEnricher);
}
