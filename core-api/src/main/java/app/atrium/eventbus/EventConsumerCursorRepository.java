package app.atrium.eventbus;

import org.springframework.data.jpa.repository.JpaRepository;

/** Public for the same reason as {@link OutboxEventRepository} — see its javadoc. */
public interface EventConsumerCursorRepository extends JpaRepository<EventConsumerCursor, String> {
}
