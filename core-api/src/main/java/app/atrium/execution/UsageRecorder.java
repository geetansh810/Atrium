package app.atrium.execution;

import app.atrium.accountability.BudgetLedger;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row per successful LLM call, idempotency key {@code taskId:attempt}
 * (13 §1.3 note: retries stay within one attempt; a redelivered task
 * increments attempt on re-claim, so distinct attempts never collide — this
 * guards the case where the SAME attempt's completion is redelivered).
 *
 * <p>{@code ON CONFLICT DO NOTHING} makes the dup-call guard a DB invariant,
 * not an app-level race: correct even under concurrent runners. MANDATORY
 * propagation — must be the same tx as the progress/complete write (13 §3.2
 * step 5/6), so a crash between them can never record spend without result.
 *
 * <p>{@code budgets.spent_tokens} (17 §M0.7) only increments when this call
 * actually wrote a new row — a deduped redelivery must not double-count spend
 * even though usage_records itself already guards the double-bill case.
 */
@Component
public class UsageRecorder {

    private final JdbcTemplate jdbc;
    private final BudgetLedger budgetLedger;

    public UsageRecorder(JdbcTemplate jdbc, BudgetLedger budgetLedger) {
        this.jdbc = jdbc;
        this.budgetLedger = budgetLedger;
    }

    /** @return true if this call wrote a new row; false if taskId:attempt was already recorded. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(UUID companyId, UUID agentId, UUID taskId, int attempt,
                          String provider, String model, long tokensIn, long tokensOut,
                          Long costMicroUsd) {
        String idempotencyKey = taskId + ":" + attempt;
        int rows = jdbc.update("""
                INSERT INTO usage_records
                    (company_id, agent_id, task_id, provider, model, tokens_in, tokens_out,
                     cost_micro_usd, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                companyId, agentId, taskId, provider, model, tokensIn, tokensOut,
                costMicroUsd, idempotencyKey);
        boolean wroteNewRow = rows == 1;
        if (wroteNewRow) {
            budgetLedger.recordSpend(companyId, agentId, tokensIn + tokensOut);
        }
        return wroteNewRow;
    }
}
