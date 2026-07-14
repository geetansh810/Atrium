package app.atrium.accountability;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ON CONFLICT (idempotency_key) DO NOTHING} makes the dup-call guard a
 * DB invariant, not an app-level race: correct even under concurrent callers.
 * MANDATORY propagation — must be the same tx as whatever else the caller is
 * recording (progress/complete for the agent loop; a memory insert for
 * LearningPipeline), so a crash between them can never record spend without
 * the corresponding result landing too.
 */
@Component
public class UsageLedgerService implements UsageLedger {

    private final JdbcTemplate jdbc;
    private final BudgetLedger budgetLedger;

    public UsageLedgerService(JdbcTemplate jdbc, BudgetLedger budgetLedger) {
        this.jdbc = jdbc;
        this.budgetLedger = budgetLedger;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(UUID companyId, UUID agentId, UUID taskId, String provider, String model,
                          long tokensIn, long tokensOut, @Nullable Long costMicroUsd, String idempotencyKey) {
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
