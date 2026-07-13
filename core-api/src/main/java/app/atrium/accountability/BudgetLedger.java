package app.atrium.accountability;

import java.util.UUID;

/**
 * The write side of spend tracking (05 §accountability, 17 §M0.7) —
 * {@code execution.UsageRecorder} calls this in the same transaction as its
 * own {@code usage_records} insert, so a redelivered (deduped) attempt never
 * double-counts spend. Never call this for a call that didn't actually
 * record a new usage_records row.
 */
public interface BudgetLedger {

    void recordSpend(UUID companyId, UUID agentId, long tokens);
}
