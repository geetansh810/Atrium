package app.atrium.agentmind.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDoc, UUID> {

    List<KnowledgeDoc> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<KnowledgeDoc> findByIdAndCompanyId(UUID id, UUID companyId);
}
