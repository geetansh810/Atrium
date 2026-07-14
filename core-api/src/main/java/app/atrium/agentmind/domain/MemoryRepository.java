package app.atrium.agentmind.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Company-scoped (03 invariant 4). Filtered listing lives in MemoryService (native query, like TaskService.list). */
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    Optional<Memory> findByIdAndCompanyId(UUID id, UUID companyId);
}
