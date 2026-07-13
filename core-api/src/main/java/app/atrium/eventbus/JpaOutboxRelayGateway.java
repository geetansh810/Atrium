package app.atrium.eventbus;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raw-SQL {@code SKIP LOCKED} claim (same idiom as task claiming / lease
 * reclaim) — deliberately not a derived query, since Spring Data JPA has no
 * portable way to express {@code FOR UPDATE SKIP LOCKED ... LIMIT}.
 */
@Component
class JpaOutboxRelayGateway implements OutboxRelayGateway {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    JpaOutboxRelayGateway(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxEvent> claimPending(int limit) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, Long.class, limit);
        return ids.stream().map(id -> entityManager.find(OutboxEvent.class, id)).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPublished(List<OutboxEvent> claimed) {
        Instant now = Instant.now();
        claimed.forEach(event -> event.markPublished(now));
    }
}
