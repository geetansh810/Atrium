package app.atrium.routing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubtaskRepository extends JpaRepository<Subtask, UUID> {

    /** Subtasks have no company_id column — tenant filter goes through the owning task. */
    @Query("""
            SELECT s FROM Subtask s
            WHERE s.taskId = :taskId
              AND EXISTS (SELECT 1 FROM Task t WHERE t.id = s.taskId AND t.companyId = :companyId)
            ORDER BY s.position
            """)
    List<Subtask> findByTaskScoped(@Param("taskId") UUID taskId, @Param("companyId") UUID companyId);
}
