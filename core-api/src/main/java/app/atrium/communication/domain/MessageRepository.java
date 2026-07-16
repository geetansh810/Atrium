package app.atrium.communication.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only chat log — insert via save(), read newest-first via the keyset query. */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Keyset page: messages strictly BEFORE the (createdAt, id) cursor position,
     * newest first (04 §Communication `?before=&limit=` history). The channel is
     * already tenant-verified by the caller before this runs.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.channelId = :channelId
              AND (m.createdAt < :beforeCreatedAt
                   OR (m.createdAt = :beforeCreatedAt AND m.id < :beforeId))
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findPageBefore(@Param("channelId") UUID channelId,
                                 @Param("beforeCreatedAt") Instant beforeCreatedAt,
                                 @Param("beforeId") UUID beforeId, Pageable pageable);
}
