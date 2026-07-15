package app.atrium.accountability.api;

import app.atrium.accountability.AnalyticsRepository.DayTotal;
import app.atrium.accountability.AnalyticsRepository.TaskCost;
import app.atrium.accountability.AnalyticsService.AgentPerformance;
import app.atrium.accountability.AnalyticsService.SkillShare;
import app.atrium.accountability.AnalyticsService.Summary;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    public record SummaryResponse(long tasksCompletedToday, long tasksApprovedToday, long tasksRejectedToday,
                                  long tokensSpentToday, long costMicroUsdToday, double successRateAllTime) {
        public static SummaryResponse from(Summary s) {
            return new SummaryResponse(s.tasksCompletedToday(), s.tasksApprovedToday(), s.tasksRejectedToday(),
                    s.tokensSpentToday(), s.costMicroUsdToday(), s.successRateAllTime());
        }
    }

    public record DayCountResponse(String day, long count) {
        public static DayCountResponse from(DayTotal d) {
            return new DayCountResponse(d.day().toString(), d.tasksCompleted());
        }
    }

    public record AgentPerformanceResponse(UUID agentId, long tasksCompleted, long tasksApproved,
                                           long tasksRejected, double successRate, long tokensSpent,
                                           long costMicroUsd) {
        public static AgentPerformanceResponse from(AgentPerformance p) {
            return new AgentPerformanceResponse(p.agentId(), p.tasksCompleted(), p.tasksApproved(),
                    p.tasksRejected(), p.successRate(), p.tokensSpent(), p.costMicroUsd());
        }
    }

    public record SkillShareResponse(String skill, long tasksCompleted, double sharePct) {
        public static SkillShareResponse from(SkillShare s) {
            return new SkillShareResponse(s.skill(), s.tasksCompleted(), s.sharePct());
        }
    }

    public record TaskCostResponse(UUID taskId, long tokens, long costMicroUsd) {
        public static TaskCostResponse from(TaskCost c) {
            return new TaskCostResponse(c.taskId(), c.tokens(), c.costMicroUsd());
        }
    }
}
