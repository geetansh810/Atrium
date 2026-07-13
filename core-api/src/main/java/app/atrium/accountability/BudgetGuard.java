package app.atrium.accountability;

import java.util.UUID;

/**
 * Claim-time budget gate (05 §accountability): routing calls this inside the
 * claim transaction; over-cap agents are refused new claims, never blocked
 * from reads. Checks both the agent's own cap and the company-wide cap for
 * the current period (17 §M0.7); a missing budget row for a scope means that
 * scope is uncapped.
 */
public interface BudgetGuard {

    boolean canSpend(UUID companyId, UUID agentId);
}
