package app.atrium.agentmind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly TTL sweep (14 §2): {@code lesson}/{@code summary} memories unused for
 * {@code atrium.memory.ttl-days} (default 90, {@code ATRIUM_MEMORY_TTL_DAYS})
 * are archived — {@code fact}/{@code preference} never expire this way, they're
 * durable by definition. Singleton via the same advisory-lock idiom as {@code
 * LeaseReclaimJob}/{@code OutboxRetentionJob}.
 */
@Component
public class MemoryTtlArchiver {

    static final long ADVISORY_LOCK_KEY = 0xA7121007L;

    private static final Logger log = LoggerFactory.getLogger(MemoryTtlArchiver.class);

    private final JdbcTemplate jdbc;
    private final int ttlDays;

    public MemoryTtlArchiver(JdbcTemplate jdbc, @Value("${atrium.memory.ttl-days:90}") int ttlDays) {
        this.jdbc = jdbc;
        this.ttlDays = ttlDays;
    }

    @Scheduled(cron = "${atrium.memory.archive-cron:0 30 3 * * *}")
    @Transactional
    public void archiveUnusedLessonsAndSummaries() {
        // M3.2: set_config(..., true) applies to every statement issued AFTER
        // it within this transaction (08 §Security rule 6) — this job is
        // deliberately cross-tenant (see class javadoc).
        jdbc.execute("SELECT set_config('app.bypass_rls', 'on', true)");

        Boolean lockHeld = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(lockHeld)) {
            return;
        }

        int archived = jdbc.update("""
                UPDATE memories
                SET status = 'archived'
                WHERE status = 'active'
                  AND kind IN ('lesson', 'summary')
                  AND COALESCE(last_used_at, created_at) < now() - make_interval(days => ?)
                """, ttlDays);
        if (archived > 0) {
            log.info("Memory TTL archiver: archived {} unused lesson/summary row(s)", archived);
        }
    }
}
