package app.atrium.routing.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDecompositionRepository extends JpaRepository<TaskDecomposition, UUID> {

    Optional<TaskDecomposition> findByCompanyIdAndParentTaskIdAndPlanArtifactId(
            UUID companyId, UUID parentTaskId, UUID planArtifactId);
}
