package app.atrium.accountability.api;

import app.atrium.accountability.domain.Budget;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public final class BudgetDtos {

    private BudgetDtos() {}

    /** Body of PUT /companies/{id}/budget (04 §Accountability). agentId null = company-wide cap. */
    public record UpsertBudgetRequest(UUID agentId, String period, @Positive long capTokens) {}

    public record BudgetResponse(
            UUID id,
            UUID agentId,
            String period,
            long capTokens,
            long spentTokens,
            short alertPct,
            Instant alertedAt) {

        public static BudgetResponse from(Budget budget) {
            return new BudgetResponse(budget.getId(), budget.getAgentId(), budget.getPeriod(),
                    budget.getCapTokens(), budget.getSpentTokens(), budget.getAlertPct(),
                    budget.getAlertedAt());
        }
    }
}
