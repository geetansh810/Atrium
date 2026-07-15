package app.atrium.accountability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Raw-JDBC aggregate reads over {@code agent_stats_daily} and {@code
 * usage_records} (17 §M2.3) — plain GROUP BY aggregation, no Hibernate
 * mapping needed for either table, matching the "raw SQL for aggregate work"
 * idiom already established by {@code WorkBroker}/{@code PgVectorMemoryStore}.
 */
@Repository
public class AnalyticsRepository {

    /** One day's totals across the whole company (all agents/skills summed). */
    public record DayTotal(LocalDate day, long tasksCompleted) {}

    public record AgentTotal(UUID agentId, long tasksCompleted, long tasksApproved,
                             long tasksRejected, long tokensSpent, long costMicroUsd) {}

    public record SkillTotal(String skill, long tasksCompleted) {}

    public record TaskCost(UUID taskId, long tokens, long costMicroUsd) {}

    public record TodayTotals(long tasksCompleted, long tasksApproved, long tasksRejected,
                              long tokensSpent, long costMicroUsd) {}

    public record ApprovalTotals(long tasksApproved, long tasksRejected) {}

    private static final RowMapper<AgentTotal> AGENT_TOTAL_MAPPER = (rs, rowNum) -> new AgentTotal(
            UUID.fromString(rs.getString("agent_id")), rs.getLong("tasks_completed"),
            rs.getLong("tasks_approved"), rs.getLong("tasks_rejected"),
            rs.getLong("tokens_spent"), rs.getLong("cost_micro_usd"));

    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TodayTotals todayTotals(UUID companyId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(tasks_completed), 0) AS tasks_completed,
                       COALESCE(SUM(tasks_approved), 0) AS tasks_approved,
                       COALESCE(SUM(tasks_rejected), 0) AS tasks_rejected,
                       COALESCE(SUM(tokens_spent), 0) AS tokens_spent,
                       COALESCE(SUM(cost_micro_usd), 0) AS cost_micro_usd
                FROM agent_stats_daily
                WHERE company_id = ? AND day = (now() AT TIME ZONE 'UTC')::date
                """,
                (rs, rowNum) -> new TodayTotals(rs.getLong("tasks_completed"), rs.getLong("tasks_approved"),
                        rs.getLong("tasks_rejected"), rs.getLong("tokens_spent"), rs.getLong("cost_micro_usd")),
                companyId);
    }

    public ApprovalTotals allTimeApprovalTotals(UUID companyId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(tasks_approved), 0) AS tasks_approved,
                       COALESCE(SUM(tasks_rejected), 0) AS tasks_rejected
                FROM agent_stats_daily WHERE company_id = ?
                """,
                (rs, rowNum) -> new ApprovalTotals(rs.getLong("tasks_approved"), rs.getLong("tasks_rejected")),
                companyId);
    }

    /** Per-day totals for the trailing {@code days} UTC days (may include zero-count days). */
    public List<DayTotal> dailyTotals(UUID companyId, int days) {
        List<DayTotal> rows = jdbc.query("""
                SELECT day, SUM(tasks_completed) AS tasks_completed
                FROM agent_stats_daily
                WHERE company_id = ? AND day >= (now() AT TIME ZONE 'UTC')::date - (? - 1) * INTERVAL '1 day'
                GROUP BY day
                """,
                (rs, rowNum) -> new DayTotal(rs.getDate("day").toLocalDate(), rs.getLong("tasks_completed")),
                companyId, days);
        Map<LocalDate, Long> byDay = rows.stream()
                .collect(java.util.stream.Collectors.toMap(DayTotal::day, DayTotal::tasksCompleted));
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> today.minusDays((long) days - 1 - i))
                .map(d -> new DayTotal(d, byDay.getOrDefault(d, 0L)))
                .toList();
    }

    /** Per-agent totals for the trailing {@code days} UTC days, desc by tasksCompleted. */
    public List<AgentTotal> agentTotals(UUID companyId, int days) {
        return jdbc.query("""
                SELECT agent_id, SUM(tasks_completed) AS tasks_completed,
                       SUM(tasks_approved) AS tasks_approved, SUM(tasks_rejected) AS tasks_rejected,
                       SUM(tokens_spent) AS tokens_spent, SUM(cost_micro_usd) AS cost_micro_usd
                FROM agent_stats_daily
                WHERE company_id = ? AND day >= (now() AT TIME ZONE 'UTC')::date - (? - 1) * INTERVAL '1 day'
                GROUP BY agent_id
                ORDER BY tasks_completed DESC
                """, AGENT_TOTAL_MAPPER, companyId, days);
    }

    /** Per-skill totals for the trailing {@code days} UTC days, desc by tasksCompleted. */
    public List<SkillTotal> skillTotals(UUID companyId, int days) {
        return jdbc.query("""
                SELECT skill, SUM(tasks_completed) AS tasks_completed
                FROM agent_stats_daily
                WHERE company_id = ? AND day >= (now() AT TIME ZONE 'UTC')::date - (? - 1) * INTERVAL '1 day'
                GROUP BY skill
                ORDER BY tasks_completed DESC
                """,
                (rs, rowNum) -> new SkillTotal(rs.getString("skill"), rs.getLong("tasks_completed")),
                companyId, days);
    }

    /** Real per-task spend for {@code [periodStart, periodEnd)}, desc by cost. */
    public List<TaskCost> costPerTask(UUID companyId, Instant periodStart, Instant periodEnd, int limit) {
        return jdbc.query("""
                SELECT task_id, SUM(tokens_in + tokens_out) AS tokens, COALESCE(SUM(cost_micro_usd), 0) AS cost
                FROM usage_records
                WHERE company_id = ? AND task_id IS NOT NULL
                  AND created_at >= ? AND created_at < ?
                GROUP BY task_id
                ORDER BY cost DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new TaskCost(UUID.fromString(rs.getString("task_id")),
                        rs.getLong("tokens"), rs.getLong("cost")),
                companyId, java.sql.Timestamp.from(periodStart), java.sql.Timestamp.from(periodEnd), limit);
    }
}
