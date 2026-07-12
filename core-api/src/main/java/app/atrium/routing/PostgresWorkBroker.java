package app.atrium.routing;

import app.atrium.accountability.BudgetGuard;
import app.atrium.common.ConflictException;
import app.atrium.common.NotFoundException;
import app.atrium.registry.AgentDirectory;
import app.atrium.registry.domain.Agent;
import app.atrium.routing.domain.Task;
import app.atrium.routing.domain.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostgresWorkBroker implements WorkBroker {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final Set<String> LEASED_STATUSES = Set.of("claimed", "in_progress");

    /**
     * The canonical claim query (03 — M0.4-amended), keyed by task id per the
     * Worker API note in 03: same SET list, same status + paused guards, same
     * FOR UPDATE SKIP LOCKED. Zero rows = lost the race or not claimable.
     */
    private static final String CLAIM_SQL = """
            UPDATE tasks SET status='claimed', assigned_agent_id=?,
              claimed_at=now(), lease_expires_at=now() + interval '10 minutes',
              attempt = attempt + 1
            WHERE id = (
              SELECT id FROM tasks
              WHERE id=? AND company_id=? AND status='queued'
                AND NOT EXISTS (SELECT 1 FROM agents a WHERE a.id=? AND a.paused)
              FOR UPDATE SKIP LOCKED
            )
            """;

    private final TaskRepository tasks;
    private final AgentDirectory agentDirectory;
    private final BudgetGuard budgetGuard;
    private final TaskEventRecorder recorder;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PostgresWorkBroker(TaskRepository tasks, AgentDirectory agentDirectory,
                              BudgetGuard budgetGuard, TaskEventRecorder recorder,
                              JdbcTemplate jdbc, EntityManager entityManager,
                              ObjectMapper objectMapper, Clock clock) {
        this.tasks = tasks;
        this.agentDirectory = agentDirectory;
        this.budgetGuard = budgetGuard;
        this.recorder = recorder;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Task claim(UUID companyId, UUID taskId, UUID agentId) {
        Agent agent = agentDirectory.findById(companyId, agentId)
                .orElseThrow(() -> NotFoundException.of("Agent", agentId));
        Task task = tasks.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));

        // Pre-flight for clearer 409s; the SQL guards stay authoritative under races.
        if (!agent.getSkillTags().contains(task.getRequiredSkill())) {
            throw new ConflictException("Agent " + agentId + " lacks required skill '"
                    + task.getRequiredSkill() + "'");
        }
        if (agent.isPaused()) {
            throw new ConflictException("Agent " + agentId + " is paused and cannot claim tasks");
        }
        if (!budgetGuard.canSpend(companyId, agentId)) {
            throw new ConflictException("Agent " + agentId + " is over budget and cannot claim tasks");
        }

        int updated = jdbc.update(CLAIM_SQL, agentId, taskId, companyId, agentId);
        if (updated == 0) {
            // Lost the race (or the state moved since our read): report who holds it.
            entityManager.refresh(task);
            String holder = task.getAssignedAgentId() != null
                    ? " — held by agent:" + task.getAssignedAgentId() : "";
            throw new ConflictException("Task " + taskId + " is '" + task.getStatus()
                    + "', not claimable" + holder + " (do not retry this claim)");
        }

        entityManager.refresh(task);    // pick up the JDBC-side claim before recording
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("agentId", agentId.toString());
        payload.put("attempt", task.getAttempt());
        payload.put("leaseExpiresAt", task.getLeaseExpiresAt().toString());
        recorder.record(task, "claimed", "agent:" + agentId, payload);
        return task;
    }

    @Override
    @Transactional
    public Task renewLease(UUID companyId, UUID taskId, UUID agentId) {
        Task task = tasks.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));

        if (!agentId.equals(task.getAssignedAgentId())) {
            String holder = task.getAssignedAgentId() != null
                    ? "held by agent:" + task.getAssignedAgentId() : "unassigned";
            throw new ConflictException("Task " + taskId + " is not leased to agent:" + agentId
                    + " (" + holder + ")");
        }
        if (!LEASED_STATUSES.contains(task.getStatus())) {
            throw new ConflictException("Task " + taskId + " is '" + task.getStatus()
                    + "' — no lease to renew");
        }
        task.renewLease(Instant.now(clock).plus(LEASE));
        return task;    // heartbeat, not a state change: no task_event (03 invariant 1 untouched)
    }
}
