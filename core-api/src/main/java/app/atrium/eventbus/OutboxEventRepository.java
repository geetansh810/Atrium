package app.atrium.eventbus;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Durable-consumer read path (M0.75+): batch strictly beyond a cursor, in id order. */
    List<OutboxEvent> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
