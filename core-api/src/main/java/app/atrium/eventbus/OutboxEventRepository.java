package app.atrium.eventbus;

import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    // Relay/cursor queries arrive with M0.75; producers only ever insert.
}
