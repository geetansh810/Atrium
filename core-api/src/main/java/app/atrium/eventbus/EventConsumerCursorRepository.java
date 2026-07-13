package app.atrium.eventbus;

import org.springframework.data.jpa.repository.JpaRepository;

interface EventConsumerCursorRepository extends JpaRepository<EventConsumerCursor, String> {
}
