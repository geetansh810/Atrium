package app.atrium.accountability.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A token cap for one period (03 §V1 + 15 §1). {@code agentId} null = the
 * company-wide cap; a UUID = that agent's own cap. Both scopes can be active
 * at once — {@code BudgetGuard} refuses a claim if either is exhausted.
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "agent_id")
    private UUID agentId;

    /** 'YYYY-MM'. */
    @Column(nullable = false)
    private String period;

    @Column(name = "cap_tokens", nullable = false)
    private long capTokens;

    @Column(name = "spent_tokens", nullable = false)
    private long spentTokens = 0;

    /** Soft-alert tier (15 §1) — default 80, i.e. 80% of capTokens. */
    @Column(name = "alert_pct", nullable = false)
    private short alertPct = 80;

    /** Once-per-period dedupe for the budget.threshold event (M0.7). */
    @Column(name = "alerted_at")
    private Instant alertedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Budget() {}

    public Budget(UUID companyId, UUID agentId, String period, long capTokens) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.period = period;
        this.capTokens = capTokens;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getAgentId() { return agentId; }
    public String getPeriod() { return period; }
    public long getCapTokens() { return capTokens; }
    public long getSpentTokens() { return spentTokens; }
    public short getAlertPct() { return alertPct; }
    public Instant getAlertedAt() { return alertedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isExhausted() {
        return spentTokens >= capTokens;
    }

    /** True once spend crosses alertPct% of the cap. */
    public boolean crossesAlertThreshold() {
        return spentTokens * 100 >= (long) alertPct * capTokens;
    }

    public void setCapTokens(long capTokens) {
        this.capTokens = capTokens;
        this.updatedAt = Instant.now();
    }

    public void addSpend(long tokens) {
        this.spentTokens += tokens;
        this.updatedAt = Instant.now();
    }

    public void markAlerted(Instant now) {
        this.alertedAt = now;
    }
}
