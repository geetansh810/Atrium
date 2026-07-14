package app.atrium.eventbus;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Public (not package-private, unlike most repos here) — M-LN1's
 * {@code agentmind.LearningPipeline} is the first {@link EventCursorWorker}
 * subclass outside this package, and its constructor parameter types must be
 * visible where it's declared for the DI wiring to compile.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Durable-consumer read path (M0.75+): batch strictly beyond a cursor, in id order. */
    List<OutboxEvent> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
