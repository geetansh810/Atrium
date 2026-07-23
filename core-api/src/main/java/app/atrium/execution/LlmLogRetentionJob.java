package app.atrium.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly purge of {@code llm_request_logs} older than the configured
 * retention window (default 30 days) — the full prompt/response bodies are
 * bulky and only useful for recent debugging, unlike {@code usage_records}
 * (billing) or {@code task_events} (the permanent audit trail), which are
 * kept indefinitely. Singleton via the same advisory-lock idiom as {@code
 * OutboxRetentionJob}/{@code MemoryTtlArchiver}; deliberately cross-tenant.
 */
@Component
public class LlmLogRetentionJob {

    static final long ADVISORY_LOCK_KEY = 0xA7121014L;

    private static final Logger log = LoggerFactory.getLogger(LlmLogRetentionJob.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public LlmLogRetentionJob(JdbcTemplate jdbc,
                              @Value("${atrium.llm-log.retention-days:30}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${atrium.llm-log.retention-cron:0 30 3 * * *}")
    @Transactional
    public void purgeOld() {
        // M3.2: set_config(..., true) applies to every statement issued AFTER it
        // within this transaction (08 §Security rule 6) — cross-tenant by design.
        jdbc.execute("SELECT set_config('app.bypass_rls', 'on', true)");

        Boolean lockHeld = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(lockHeld)) {
            return;
        }

        int deleted = jdbc.update(
                "DELETE FROM llm_request_logs WHERE created_at < now() - make_interval(days => ?)",
                retentionDays);
        if (deleted > 0) {
            log.info("LLM log retention: purged {} row(s) older than {} days", deleted, retentionDays);
        }
    }
}
