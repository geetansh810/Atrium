package app.atrium.accountability.api;

import app.atrium.accountability.AnalyticsService;
import app.atrium.accountability.api.AnalyticsDtos.AgentPerformanceResponse;
import app.atrium.accountability.api.AnalyticsDtos.DayCountResponse;
import app.atrium.accountability.api.AnalyticsDtos.SkillShareResponse;
import app.atrium.accountability.api.AnalyticsDtos.SummaryResponse;
import app.atrium.accountability.api.AnalyticsDtos.TaskCostResponse;
import app.atrium.common.NotFoundException;
import app.atrium.common.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 04 §Accountability's analytics rollup endpoints (17 §M2.3). */
@RestController
@RequestMapping("/api/v1/companies/{id}/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(@PathVariable UUID id) {
        requireTenantMatch(id);
        return SummaryResponse.from(analytics.summary(id));
    }

    @GetMapping("/tasks-7d")
    public List<DayCountResponse> tasks7d(@PathVariable UUID id) {
        requireTenantMatch(id);
        return analytics.tasks7d(id).stream().map(DayCountResponse::from).toList();
    }

    @GetMapping("/agent-performance")
    public List<AgentPerformanceResponse> agentPerformance(@PathVariable UUID id,
                                                            @RequestParam(required = false) Integer days) {
        requireTenantMatch(id);
        return analytics.agentPerformance(id, days).stream().map(AgentPerformanceResponse::from).toList();
    }

    @GetMapping("/top-skills")
    public List<SkillShareResponse> topSkills(@PathVariable UUID id,
                                              @RequestParam(required = false) Integer days) {
        requireTenantMatch(id);
        return analytics.topSkills(id, days).stream().map(SkillShareResponse::from).toList();
    }

    @GetMapping("/cost-per-task")
    public List<TaskCostResponse> costPerTask(@PathVariable UUID id,
                                              @RequestParam(required = false) String period,
                                              @RequestParam(required = false) Integer limit) {
        requireTenantMatch(id);
        return analytics.costPerTask(id, period, limit).stream().map(TaskCostResponse::from).toList();
    }

    private void requireTenantMatch(UUID pathCompanyId) {
        if (!TenantContext.requireCompanyId().equals(pathCompanyId)) {
            throw NotFoundException.of("Company", pathCompanyId);
        }
    }
}
