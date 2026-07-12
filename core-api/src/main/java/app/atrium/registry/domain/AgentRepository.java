package app.atrium.registry.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Invariant 03 §4: no method without a companyId parameter. */
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findByCompanyIdOrderByJoinedAt(UUID companyId);

    Optional<Agent> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query(value = "SELECT * FROM agents WHERE company_id = :companyId AND :skill = ANY(skill_tags)",
           nativeQuery = true)
    List<Agent> findByCompanyIdAndSkill(@Param("companyId") UUID companyId, @Param("skill") String skill);
}
