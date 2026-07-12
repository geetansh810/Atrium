package app.atrium.accountability;

import java.util.UUID;

/**
 * Claim-time budget gate (05 §accountability): routing calls this inside the
 * claim transaction; over-cap agents are refused new claims, never blocked
 * from reads. Real enforcement (agent cap AND company-wide cap rows,
 * auto-pause) lands at M0.7.
 */
public interface BudgetGuard {

    boolean canSpend(UUID companyId, UUID agentId);
}
