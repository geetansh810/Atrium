package app.atrium.execution;

import app.atrium.accountability.UsageLedger;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row per successful LLM call, idempotency key {@code taskId:attempt}
 * (13 §1.3 note: retries stay within one attempt; a redelivered task
 * increments attempt on re-claim, so distinct attempts never collide — this
 * guards the case where the SAME attempt's completion is redelivered).
 *
 * <p>The actual insert-once-then-record-spend logic lives in {@link
 * UsageLedger} (accountability, M-LN1) — shared with {@code
 * agentmind.LearningPipeline}'s {@code learn:{eventId}}-keyed extraction
 * calls, which need the identical dup-guard behavior. This class now only
 * owns the {@code taskId:attempt} key format, MANDATORY propagation still
 * enforced by the shared implementation (must be the same tx as the
 * progress/complete write, 13 §3.2 step 5/6).
 */
@Component
public class UsageRecorder {

    private final UsageLedger usageLedger;

    public UsageRecorder(UsageLedger usageLedger) {
        this.usageLedger = usageLedger;
    }

    /** @return true if this call wrote a new row; false if taskId:attempt was already recorded. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(UUID companyId, UUID agentId, UUID taskId, int attempt,
                          String provider, String model, long tokensIn, long tokensOut,
                          Long costMicroUsd) {
        String idempotencyKey = taskId + ":" + attempt;
        return usageLedger.record(companyId, agentId, taskId, provider, model,
                tokensIn, tokensOut, costMicroUsd, idempotencyKey);
    }
}
