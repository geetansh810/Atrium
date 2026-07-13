package app.atrium.accountability.api;

import app.atrium.accountability.BudgetService;
import app.atrium.accountability.api.BudgetDtos.BudgetResponse;
import app.atrium.accountability.api.BudgetDtos.UpsertBudgetRequest;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{id}/budget")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /** Caps + spend, company-wide and per-agent, for one period (04 §Accountability). */
    @GetMapping
    public List<BudgetResponse> list(@PathVariable UUID id,
                                     @RequestParam(required = false) String period) {
        requireTenantMatch(id);
        return budgetService.list(id, period).stream().map(BudgetResponse::from).toList();
    }

    @PutMapping
    public BudgetResponse upsert(@PathVariable UUID id, @Valid @RequestBody UpsertBudgetRequest request) {
        requireTenantMatch(id);
        return BudgetResponse.from(
                budgetService.upsert(id, request.agentId(), request.period(), request.capTokens()));
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
