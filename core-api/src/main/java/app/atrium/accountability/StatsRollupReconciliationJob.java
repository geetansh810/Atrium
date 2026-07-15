package app.atrium.accountability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly full recompute of the trailing 2 UTC days of {@code agent_stats_daily}
 * (17 §M2.3; the "nightly" half of 03's "analytics rollup (nightly + on-approve)"
 * comment — {@link StatsRollupWorker} is the on-approve half). A genuine
 * drift-correcting safety net, not a redundant re-run: it recomputes straight
 * from {@code outbox_events} (still retained — {@code OutboxRetentionJob} never
 * purges a row ahead of any durable consumer's cursor, this one included) rather
 * than replaying through {@link StatsRollupWorker}'s own additive upserts, which
 * would double-count anything the cursor already advanced past. Singleton via
 * the same advisory-lock idiom as {@code LeaseReclaimJob}/{@code
 * OutboxRetentionJob}/{@code MemoryTtlArchiver}.
 */
@Component
public class StatsRollupReconciliationJob {

    static final long ADVISORY_LOCK_KEY = 0xA7121008L;

    private static final Logger log = LoggerFactory.getLogger(StatsRollupReconciliationJob.class);

    private final JdbcTemplate jdbc;

    public StatsRollupReconciliationJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${atrium.stats-rollup.reconcile-cron:0 15 3 * * *}")
    @Transactional
    public void reconcile() {
        Boolean lockHeld = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(lockHeld)) {
            return;
        }

        int deleted = jdbc.update("""
                DELETE FROM agent_stats_daily
                WHERE day >= (now() AT TIME ZONE 'UTC')::date - INTERVAL '1 day'
                """);

        int inserted = jdbc.update("""
                INSERT INTO agent_stats_daily
                    (company_id, agent_id, skill, day, tasks_completed, tasks_approved,
                     tasks_rejected, tokens_spent, cost_micro_usd)
                SELECT o.company_id,
                       (o.payload->>'agentId')::uuid,
                       o.payload->>'requiredSkill',
                       (o.created_at AT TIME ZONE 'UTC')::date,
                       SUM(CASE WHEN o.event_type = 'task.completed' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN o.event_type = 'task.approved' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN o.event_type = 'task.rejected' THEN 1 ELSE 0 END),
                       COALESCE(SUM(u.tokens_in + u.tokens_out), 0),
                       COALESCE(SUM(u.cost_micro_usd), 0)
                FROM outbox_events o
                LEFT JOIN usage_records u
                    ON o.event_type = 'task.completed'
                   AND u.idempotency_key = (o.payload ->> 'taskId') || ':' || (o.payload ->> 'attempt')
                WHERE o.event_type IN ('task.completed', 'task.approved', 'task.rejected')
                  AND o.payload ? 'agentId' AND o.payload ? 'requiredSkill'
                  AND (o.created_at AT TIME ZONE 'UTC')::date
                      >= (now() AT TIME ZONE 'UTC')::date - INTERVAL '1 day'
                GROUP BY 1, 2, 3, 4
                """);
        log.info("Stats rollup reconciliation: replaced {} row(s) with {} recomputed row(s)", deleted, inserted);
    }
}
