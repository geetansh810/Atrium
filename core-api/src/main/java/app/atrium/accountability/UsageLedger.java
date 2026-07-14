package app.atrium.accountability;

import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * The write side of {@code usage_records} (05 §accountability) — one row per
 * successful LLM call, deduped on {@code idempotencyKey}. {@code
 * execution.UsageRecorder} (the agent loop's caller, {@code taskId:attempt}
 * keys) and {@code agentmind.LearningPipeline} (extraction calls, {@code
 * learn:{eventId}} keys, M-LN1: "learning is payroll too", 14 §5) are the two
 * callers — both need the same insert-once-then-record-spend behavior, so it
 * lives here rather than duplicated per caller.
 */
public interface UsageLedger {

    /** @return true if this call wrote a new row; false if idempotencyKey was already recorded. */
    boolean record(UUID companyId, UUID agentId, UUID taskId, String provider, String model,
                   long tokensIn, long tokensOut, @Nullable Long costMicroUsd, String idempotencyKey);
}
