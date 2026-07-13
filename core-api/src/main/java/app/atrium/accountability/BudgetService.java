package app.atrium.accountability;

import app.atrium.accountability.domain.Budget;
import app.atrium.accountability.domain.BudgetRepository;
import app.atrium.common.FieldValidationException;
import app.atrium.eventbus.OutboxWriter;
import app.atrium.eventbus.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budgets CRUD + the real {@link BudgetGuard}/{@link BudgetLedger} (17 §M0.7,
 * supersedes {@code NoopBudgetGuard}). A missing budget row for a scope
 * (agent or company-wide) means that scope is uncapped — budgets are opt-in.
 */
@Service
public class BudgetService implements BudgetGuard, BudgetLedger {

    private final BudgetRepository budgets;
    private final BudgetBreachRecorder breachRecorder;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BudgetService(BudgetRepository budgets, BudgetBreachRecorder breachRecorder,
                         OutboxWriter outboxWriter, ObjectMapper objectMapper, Clock clock) {
        this.budgets = budgets;
        this.breachRecorder = breachRecorder;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Budget> list(UUID companyId, String period) {
        return budgets.findByCompanyIdAndPeriod(companyId, period != null ? period : currentPeriod());
    }

    /** Upsert by the natural key (company_id, agent_id, period) — 03's UNIQUE constraint. */
    @Transactional
    public Budget upsert(UUID companyId, UUID agentId, String period, long capTokens) {
        if (capTokens <= 0) {
            throw new FieldValidationException(Map.of("capTokens", "must be positive"));
        }
        String resolvedPeriod = period != null ? period : currentPeriod();
        Optional<Budget> existing = agentId != null
                ? budgets.findByCompanyIdAndAgentIdAndPeriod(companyId, agentId, resolvedPeriod)
                : budgets.findByCompanyIdAndAgentIdIsNullAndPeriod(companyId, resolvedPeriod);
        if (existing.isPresent()) {
            existing.get().setCapTokens(capTokens);
            return existing.get();
        }
        return budgets.save(new Budget(companyId, agentId, resolvedPeriod, capTokens));
    }

    /**
     * Claim-time gate (17 §M0.7): checks the agent's own cap AND the
     * company-wide cap for the current period. An agent-scope breach
     * auto-pauses that agent (Paperclip hard-ceiling); a company-wide breach
     * refuses the claim without picking an arbitrary agent to pause, since
     * the cap is shared.
     */
    @Override
    @Transactional
    public boolean canSpend(UUID companyId, UUID agentId) {
        String period = currentPeriod();
        Optional<Budget> agentBudget =
                budgets.findByCompanyIdAndAgentIdAndPeriod(companyId, agentId, period);
        Optional<Budget> companyBudget =
                budgets.findByCompanyIdAndAgentIdIsNullAndPeriod(companyId, period);

        boolean agentExhausted = agentBudget.map(Budget::isExhausted).orElse(false);
        boolean companyExhausted = companyBudget.map(Budget::isExhausted).orElse(false);

        if (agentExhausted) {
            breachRecorder.recordAgentBreach(companyId, agentId, period);
        } else if (companyExhausted) {
            breachRecorder.recordCompanyBreach(companyId, period);
        }
        return !(agentExhausted || companyExhausted);
    }

    /**
     * Spend tracking (17 §M0.7): {@code execution.UsageRecorder} calls this in
     * the same transaction as its own idempotent insert, so a deduped
     * (redelivered) call never reaches here twice for the same attempt.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSpend(UUID companyId, UUID agentId, long tokens) {
        String period = currentPeriod();
        budgets.findByCompanyIdAndAgentIdAndPeriod(companyId, agentId, period)
                .ifPresent(b -> addSpendAndMaybeAlert(b, tokens, companyId, agentId));
        budgets.findByCompanyIdAndAgentIdIsNullAndPeriod(companyId, period)
                .ifPresent(b -> addSpendAndMaybeAlert(b, tokens, companyId, null));
    }

    private void addSpendAndMaybeAlert(Budget budget, long tokens, UUID companyId, UUID alertAgentId) {
        budget.addSpend(tokens);
        if (budget.getAlertedAt() == null && budget.crossesAlertThreshold()) {
            budget.markAlerted(Instant.now(clock));
            ObjectNode payload = objectMapper.createObjectNode();
            if (alertAgentId != null) payload.put("agentId", alertAgentId.toString());
            payload.put("period", budget.getPeriod());
            payload.put("spentTokens", budget.getSpentTokens());
            payload.put("capTokens", budget.getCapTokens());
            payload.put("pct", (int) (budget.getSpentTokens() * 100 / budget.getCapTokens()));
            outboxWriter.append(companyId, Topics.budget(companyId, alertAgentId),
                    "budget.threshold", payload);
        }
    }

    private String currentPeriod() {
        return YearMonth.now(clock).toString();
    }
}
