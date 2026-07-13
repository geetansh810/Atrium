package app.atrium.accountability.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    /** The company-wide cap for a period — agentId is NULL, not "any agent". */
    Optional<Budget> findByCompanyIdAndAgentIdIsNullAndPeriod(UUID companyId, String period);

    Optional<Budget> findByCompanyIdAndAgentIdAndPeriod(UUID companyId, UUID agentId, String period);

    List<Budget> findByCompanyIdAndPeriod(UUID companyId, String period);
}
