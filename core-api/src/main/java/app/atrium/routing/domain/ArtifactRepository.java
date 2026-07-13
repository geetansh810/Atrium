package app.atrium.routing.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    Optional<Artifact> findFirstByTaskIdAndCompanyIdOrderByCreatedAtDesc(UUID taskId, UUID companyId);
}
