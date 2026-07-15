package app.atrium.accountability;

import app.atrium.accountability.AnalyticsRepository.AgentTotal;
import app.atrium.accountability.AnalyticsRepository.ApprovalTotals;
import app.atrium.accountability.AnalyticsRepository.DayTotal;
import app.atrium.accountability.AnalyticsRepository.SkillTotal;
import app.atrium.accountability.AnalyticsRepository.TaskCost;
import app.atrium.accountability.AnalyticsRepository.TodayTotals;
import app.atrium.common.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 04 §Accountability's four rollup-backed endpoints + the M2.3-added cost-per-task one. */
@Service
public class AnalyticsService {

    public record Summary(long tasksCompletedToday, long tasksApprovedToday, long tasksRejectedToday,
                          long tokensSpentToday, long costMicroUsdToday, double successRateAllTime) {}

    public record AgentPerformance(UUID agentId, long tasksCompleted, long tasksApproved,
                                   long tasksRejected, double successRate, long tokensSpent, long costMicroUsd) {}

    public record SkillShare(String skill, long tasksCompleted, double sharePct) {}

    private final AnalyticsRepository analytics;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID companyId) {
        TodayTotals today = analytics.todayTotals(companyId);
        ApprovalTotals allTime = analytics.allTimeApprovalTotals(companyId);
        return new Summary(today.tasksCompleted(), today.tasksApproved(), today.tasksRejected(),
                today.tokensSpent(), today.costMicroUsd(), successRate(allTime.tasksApproved(), allTime.tasksRejected()));
    }

    @Transactional(readOnly = true)
    public List<DayTotal> tasks7d(UUID companyId) {
        return analytics.dailyTotals(companyId, 7);
    }

    @Transactional(readOnly = true)
    public List<AgentPerformance> agentPerformance(UUID companyId, Integer days) {
        int window = clampDays(days);
        List<AgentTotal> totals = analytics.agentTotals(companyId, window);
        return totals.stream()
                .map(t -> new AgentPerformance(t.agentId(), t.tasksCompleted(), t.tasksApproved(),
                        t.tasksRejected(), successRate(t.tasksApproved(), t.tasksRejected()),
                        t.tokensSpent(), t.costMicroUsd()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SkillShare> topSkills(UUID companyId, Integer days) {
        int window = clampDays(days);
        List<SkillTotal> totals = analytics.skillTotals(companyId, window);
        long total = totals.stream().mapToLong(SkillTotal::tasksCompleted).sum();
        return totals.stream()
                .map(t -> new SkillShare(t.skill(), t.tasksCompleted(),
                        total > 0 ? Math.round(t.tasksCompleted() * 1000.0 / total) / 10.0 : 0.0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskCost> costPerTask(UUID companyId, String period, Integer limit) {
        YearMonth month = parsePeriod(period);
        Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int clampedLimit = clampLimit(limit);
        return analytics.costPerTask(companyId, start, end, clampedLimit);
    }

    private double successRate(long approved, long rejected) {
        long terminal = approved + rejected;
        return terminal > 0 ? Math.round(approved * 1000.0 / terminal) / 10.0 : 0.0;
    }

    private int clampDays(Integer days) {
        if (days == null) return 7;
        if (days < 1 || days > 90) {
            throw new FieldValidationException(Map.of("days", "must be between 1 and 90"));
        }
        return days;
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return 10;
        if (limit < 1 || limit > 50) {
            throw new FieldValidationException(Map.of("limit", "must be between 1 and 50"));
        }
        return limit;
    }

    private YearMonth parsePeriod(String period) {
        if (period == null) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new FieldValidationException(Map.of("period", "must be 'YYYY-MM'"));
        }
    }
}
