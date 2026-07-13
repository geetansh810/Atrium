package app.atrium.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly cleanup (15 §2): delete published rows older than 14 days that are
 * also at-or-below every durable consumer's cursor — {@code task_events}
 * stays the permanent audit trail, {@code outbox_events} is transport only.
 * With no consumer rows yet, the cursor floor is vacuously "no limit"
 * (COALESCE to {@code Long.MAX_VALUE}), so age alone governs until M-LN1.
 * Singleton via the same advisory-lock idiom as {@code LeaseReclaimJob}.
 */
@Component
public class OutboxRetentionJob {

    static final long ADVISORY_LOCK_KEY = 0xA7121006L;

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final JdbcTemplate jdbc;

    public OutboxRetentionJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${atrium.relay.retention-cron:0 0 3 * * *}")
    @Transactional
    public void purgeOldPublished() {
        Boolean lockHeld = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(lockHeld)) {
            return;
        }

        int deleted = jdbc.update("""
                DELETE FROM outbox_events
                WHERE published_at IS NOT NULL
                  AND created_at < now() - interval '14 days'
                  AND id <= COALESCE((SELECT MIN(last_event_id) FROM event_consumers), 9223372036854775807)
                """);
        if (deleted > 0) {
            log.info("Outbox retention: purged {} published row(s)", deleted);
        }
    }
}
