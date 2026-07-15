package app.atrium.accountability;

import app.atrium.eventbus.EventConsumerCursorRepository;
import app.atrium.eventbus.EventCursorWorker;
import app.atrium.eventbus.OutboxEvent;
import app.atrium.eventbus.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The durable consumer (12 §3) behind {@code agent_stats_daily}'s "on-approve"
 * half (17 §M2.3; nightly half is {@link StatsRollupReconciliationJob}).
 * Consumes {@code task.completed|approved|rejected} straight from the outbox
 * payload only — never reaches into routing's {@code Task}/{@code
 * TaskRepository} (accountability has no dependency on routing; the reverse
 * direction is the documented one, 12 §2) — so {@code TaskService} was
 * extended to put {@code requiredSkill} (and, for completion, {@code
 * attempt}) directly on those three event payloads (M2.3).
 *
 * <p>Upserts are additive ({@code tasks_completed = tasks_completed + 1},
 * not an overwrite) — safe under the base class's REAL crash guarantee (a
 * failure mid-batch rolls the writes AND the cursor back together, one
 * transaction, so the next poll starts the whole batch over from a state
 * where none of it was ever applied — same "at-least-once, not exactly-once
 * under a forced replay" posture {@code OutboxRelay} documents for its own
 * redelivery case). It is NOT safe against a manually forced cursor rewind
 * (something no real operational path does, unlike {@code UsageLedger}'s
 * hard-uniqueness-keyed rows, which stay correct even under that) — the
 * nightly {@link StatsRollupReconciliationJob} is the actual drift-correcting
 * safety net for whatever this consumer might still get wrong.
 */
@Component
public class StatsRollupWorker extends EventCursorWorker {

    static final String CONSUMER_NAME = "stats_rollup";
    private static final Set<String> HANDLED_EVENT_TYPES =
            Set.of("task.completed", "task.approved", "task.rejected");

    private static final Logger log = LoggerFactory.getLogger(StatsRollupWorker.class);

    private final JdbcTemplate jdbc;
    private final int batchSize;
    private final StatsRollupWorker self;

    public StatsRollupWorker(OutboxEventRepository outbox, EventConsumerCursorRepository cursors,
                             JdbcTemplate jdbc,
                             @Value("${atrium.stats-rollup.batch-size:50}") int batchSize,
                             @Lazy StatsRollupWorker self) {
        super(CONSUMER_NAME, outbox, cursors);
        this.jdbc = jdbc;
        this.batchSize = batchSize;
        // Same @Lazy-self idiom as LearningPipeline/JpaAgentDirectory: pollOnce()
        // is inherited + @Transactional, so an unqualified this.pollOnce(...) call
        // from the @Scheduled method would bypass Spring's proxy entirely.
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${atrium.stats-rollup.poll-interval-ms:5000}")
    public void poll() {
        self.pollOnce(batchSize);
    }

    @Override
    protected void handle(OutboxEvent event) {
        if (!HANDLED_EVENT_TYPES.contains(event.getEventType())) {
            return;
        }
        JsonNode payload = event.getPayload();
        if (payload == null || !payload.hasNonNull("agentId") || !payload.hasNonNull("requiredSkill")) {
            log.warn("stats_rollup: skipping {} event {} — missing agentId/requiredSkill in payload",
                    event.getEventType(), event.getId());
            return;
        }
        UUID companyId = event.getCompanyId();
        UUID agentId = UUID.fromString(payload.get("agentId").asText());
        String skill = payload.get("requiredSkill").asText();
        String day = event.getCreatedAt().atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString();

        long tokens = 0;
        long costMicroUsd = 0;
        int completed = 0;
        int approved = 0;
        int rejected = 0;
        switch (event.getEventType()) {
            case "task.completed" -> {
                completed = 1;
                if (payload.hasNonNull("taskId") && payload.hasNonNull("attempt")) {
                    String idempotencyKey = payload.get("taskId").asText() + ":" + payload.get("attempt").asInt();
                    long[] usage = usageFor(idempotencyKey);
                    tokens = usage[0];
                    costMicroUsd = usage[1];
                }
            }
            case "task.approved" -> approved = 1;
            case "task.rejected" -> rejected = 1;
            default -> throw new IllegalStateException("unreachable — filtered by HANDLED_EVENT_TYPES");
        }

        jdbc.update("""
                INSERT INTO agent_stats_daily
                    (company_id, agent_id, skill, day, tasks_completed, tasks_approved,
                     tasks_rejected, tokens_spent, cost_micro_usd)
                VALUES (?, ?, ?, ?::date, ?, ?, ?, ?, ?)
                ON CONFLICT (company_id, agent_id, skill, day) DO UPDATE SET
                    tasks_completed = agent_stats_daily.tasks_completed + EXCLUDED.tasks_completed,
                    tasks_approved  = agent_stats_daily.tasks_approved  + EXCLUDED.tasks_approved,
                    tasks_rejected  = agent_stats_daily.tasks_rejected  + EXCLUDED.tasks_rejected,
                    tokens_spent    = agent_stats_daily.tokens_spent    + EXCLUDED.tokens_spent,
                    cost_micro_usd  = agent_stats_daily.cost_micro_usd  + EXCLUDED.cost_micro_usd
                """,
                companyId, agentId, skill, day, completed, approved, rejected, tokens, costMicroUsd);
    }

    private long[] usageFor(String idempotencyKey) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(tokens_in + tokens_out), 0), COALESCE(SUM(cost_micro_usd), 0)
                FROM usage_records WHERE idempotency_key = ?
                """, (rs, rowNum) -> new long[] {rs.getLong(1), rs.getLong(2)}, idempotencyKey);
    }
}
