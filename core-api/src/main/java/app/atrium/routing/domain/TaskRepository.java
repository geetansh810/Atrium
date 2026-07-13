package app.atrium.routing.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Approve gate (03 invariant 5): true if any child task isn't shipped/dropped yet. */
    boolean existsByParentTaskIdAndCompanyIdAndStatusNotIn(
            UUID parentTaskId, UUID companyId, Collection<String> terminalStatuses);
}
