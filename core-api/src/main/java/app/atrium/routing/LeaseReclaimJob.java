package app.atrium.routing;

import app.atrium.routing.domain.Task;
import app.atrium.routing.domain.TaskStateGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requeues expired leases (03 §reclaim): automatic but never silent — every
 * requeue is a {@code task_events(requeued)} + outbox row. Singleton across
 * instances via a Postgres advisory lock (12 §8.3; the xact variant releases
 * with the transaction, so a crashed run can't wedge the lock).
 *
 * <p>Deliberately cross-tenant: this is system infrastructure sweeping every
 * company's queue, not a tenant read path — hence raw SQL, not a repository.
 */
@Component
public class LeaseReclaimJob {

    /** Arbitrary fixed key naming this singleton job cluster-wide. */
    static final long ADVISORY_LOCK_KEY = 0xA7121004L;

    private static final Logger log = LoggerFactory.getLogger(LeaseReclaimJob.class);

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final TaskEventRecorder recorder;
    private final ObjectMapper objectMapper;

    public LeaseReclaimJob(JdbcTemplate jdbc, EntityManager entityManager,
                           TaskEventRecorder recorder, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${atrium.lease.reclaim-ms:60000}")
    @Transactional
    public void reclaimExpiredLeases() {
        Boolean lockHeld = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(lockHeld)) {
            return;     // another instance is sweeping
        }

        // SKIP LOCKED: never contend with an in-flight claim/renew transaction.
        List<UUID> expired = jdbc.queryForList("""
                SELECT id FROM tasks
                WHERE status IN ('claimed','in_progress') AND lease_expires_at < now()
                FOR UPDATE SKIP LOCKED
                """, UUID.class);

        for (UUID taskId : expired) {
            Task task = entityManager.find(Task.class, taskId);
            UUID previousAgentId = task.getAssignedAgentId();
            TaskStateGuard.transition(task, "queued");
            task.clearAssignment();

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("reason", "lease_expired");
            payload.put("attempt", task.getAttempt());
            if (previousAgentId != null) {
                payload.put("previousAgentId", previousAgentId.toString());
            }
            recorder.record(task, "requeued", "system", payload);
        }
        if (!expired.isEmpty()) {
            log.info("Lease reclaim: requeued {} expired task(s)", expired.size());
        }
    }
}
