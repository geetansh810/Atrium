package app.atrium.routing.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Escalations surface (04 §Tasks, M2.5): everything needing a human — flagged
     *  or awaiting review — newest first. */
    List<Task> findByCompanyIdAndStatusInOrderByCreatedAtDesc(UUID companyId, Collection<String> statuses);

    /** Approve gate (03 invariant 5): true if any child task isn't shipped/dropped yet. */
    boolean existsByParentTaskIdAndCompanyIdAndStatusNotIn(
            UUID parentTaskId, UUID companyId, Collection<String> terminalStatuses);

    /** Task Flow graph (M2.2, 04 §Tasks): every task reachable from {@code rootId} via
     *  parent_task_id, root included. Depth is small in practice (request_depth tracks
     *  hops) but a recursive CTE stays correct for however deep a chain grows. */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT * FROM tasks WHERE id = :rootId AND company_id = :companyId
                UNION ALL
                SELECT t.* FROM tasks t
                JOIN descendants d ON t.parent_task_id = d.id
                WHERE t.company_id = :companyId
            )
            SELECT * FROM descendants
            """, nativeQuery = true)
    List<Task> findDescendants(@Param("companyId") UUID companyId, @Param("rootId") UUID rootId);
}
