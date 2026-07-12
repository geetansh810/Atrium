package app.atrium.routing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only: insert via save(), read via the scoped queries — no updates, ever. */
public interface TaskEventRepository extends JpaRepository<TaskEvent, UUID> {

    List<TaskEvent> findByTaskIdAndCompanyIdOrderByCreatedAtAscIdAsc(UUID taskId, UUID companyId);

    /** Keyset page: events strictly after the (createdAt, id) cursor position, oldest first. */
    @Query("""
            SELECT e FROM TaskEvent e
            WHERE e.taskId = :taskId AND e.companyId = :companyId
              AND (e.createdAt > :afterCreatedAt
                   OR (e.createdAt = :afterCreatedAt AND e.id > :afterId))
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<TaskEvent> findPageAfter(@Param("taskId") UUID taskId, @Param("companyId") UUID companyId,
                                  @Param("afterCreatedAt") java.time.Instant afterCreatedAt,
                                  @Param("afterId") UUID afterId, Pageable pageable);
}
