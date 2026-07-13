package app.atrium.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * No concrete durable consumer exists yet (M-LN1's LearningPipeline / M2.3's
 * StatsRollup are the first) — this pins {@link EventCursorWorker}'s
 * poll/advance/resume contract against a throwaway subclass so the base is
 * proven before anything depends on it.
 */
class EventCursorWorkerTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestWorker worker;

    private Long insertRow(UUID companyId) {
        return jdbc.queryForObject("""
                INSERT INTO outbox_events(company_id, topic, event_type, payload)
                VALUES (?, 'test/topic', 'test.event', '{}'::jsonb)
                RETURNING id
                """, Long.class, companyId);
    }

    @Test
    void pollsBatchAdvancesCursorAndNeverRedeliversHandledRows() {
        // Seed the cursor past whatever the (Testcontainers-reused) outbox
        // table already holds — this worker's contract is about NEW rows
        // beyond its own cursor, not a claim on a pristine table.
        long baseline = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM outbox_events", Long.class);
        jdbc.update("INSERT INTO event_consumers(consumer_name, last_event_id) VALUES (?, ?)",
                worker.getConsumerName(), baseline);

        UUID companyId = UUID.randomUUID();
        Long id1 = insertRow(companyId);
        Long id2 = insertRow(companyId);

        worker.pollOnce(10);
        assertThat(worker.getHandledIds()).containsExactly(id1, id2);

        worker.pollOnce(10);    // nothing new beyond the cursor
        assertThat(worker.getHandledIds()).containsExactly(id1, id2);

        Long id3 = insertRow(companyId);
        worker.pollOnce(10);
        assertThat(worker.getHandledIds()).containsExactly(id1, id2, id3);
    }

    static class TestWorker extends EventCursorWorker {
        // Not a plain field read in assertions: pollOnce runs behind a
        // @Transactional CGLIB proxy, so the bean the test holds and the
        // target instance the proxy delegates to are different objects —
        // a direct field read on the proxy would see its own (never
        // constructed) copy. A getter, like pollOnce itself, is proxied
        // and correctly delegates to the target.
        private final List<Long> handledIds = new CopyOnWriteArrayList<>();

        TestWorker(OutboxEventRepository outbox, EventConsumerCursorRepository cursors) {
            super("test_consumer_" + UUID.randomUUID(), outbox, cursors);
        }

        @Override
        protected void handle(OutboxEvent event) {
            handledIds.add(event.getId());
        }

        List<Long> getHandledIds() {
            return handledIds;
        }

        String getConsumerName() {
            return consumerName();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        TestWorker testWorker(OutboxEventRepository outbox, EventConsumerCursorRepository cursors) {
            return new TestWorker(outbox, cursors);
        }
    }
}
