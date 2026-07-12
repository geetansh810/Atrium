package app.atrium.accountability;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stub until M0.7 (17 §M0.4): everyone can spend. Replaced by the real cap check. */
@Component
public class NoopBudgetGuard implements BudgetGuard {

    @Override
    public boolean canSpend(UUID companyId, UUID agentId) {
        return true;
    }
}
