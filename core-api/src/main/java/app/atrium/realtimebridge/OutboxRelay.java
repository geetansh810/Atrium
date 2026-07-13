package app.atrium.realtimebridge;

import app.atrium.eventbus.OutboxEvent;
import app.atrium.eventbus.OutboxRelayGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays the transactional outbox to Redis pub/sub (12 §3) — the one-way
 * bridge to office/dashboard projections. Batch claim + Redis publish +
 * {@code published_at} stamp all happen inside ONE transaction: if the
 * process dies between claiming and committing, nothing was ever marked
 * published, so the next tick's {@code SKIP LOCKED} claim picks the same
 * rows back up. At-least-once — consumers (office-realtime) must be
 * idempotent, which they already are via full-state self-heal from
 * {@code GET /office-state}.
 *
 * <p>Singleton across instances via the same advisory-lock idiom as
 * {@code LeaseReclaimJob} — a different fixed key so the two locks never
 * collide.
 */
@Component
public class OutboxRelay {

    static final long ADVISORY_LOCK_KEY = 0xA7121005L;
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final OutboxRelayGateway gateway;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingCount = new AtomicLong();

    public OutboxRelay(JdbcTemplate jdbc, OutboxRelayGateway gateway, StringRedisTemplate redis,
                       ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("atrium.relay.pending", pendingCount);
    }

    @Scheduled(fixedDelayString = "${atrium.relay.interval-ms:250}")
    @Transactional
    public void relayBatch() {
        Boolean lockHeld = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
        if (!Boolean.TRUE.equals(lockHeld)) {
            return;     // another instance is relaying
        }

        List<OutboxEvent> batch = gateway.claimPending(BATCH_SIZE);
        if (!batch.isEmpty()) {
            Instant now = Instant.now();
            for (OutboxEvent event : batch) {
                publish(event, now);
            }
            gateway.markPublished(batch);
        }

        Long pending = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
        pendingCount.set(pending == null ? 0L : pending);
    }

    private void publish(OutboxEvent event, Instant now) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", event.getEventType());
        JsonNode payload = event.getPayload();
        if (payload != null && payload.isObject()) {
            message.setAll((ObjectNode) payload);
        }
        message.put("ts", now.toString());

        redis.convertAndSend("atrium:events:" + event.getCompanyId(), message.toString());
        meterRegistry.timer("atrium.relay.lag").record(Duration.between(event.getCreatedAt(), now));
    }
}
