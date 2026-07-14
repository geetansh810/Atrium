package app.atrium.agentmind.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Company-scoped except for global skills (company_id IS NULL), visible to every tenant. */
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    @Query("SELECT s FROM Skill s WHERE s.companyId = :companyId OR s.companyId IS NULL "
            + "ORDER BY s.key, s.version DESC")
    List<Skill> findVisibleToCompany(@Param("companyId") UUID companyId);

    @Query("SELECT s FROM Skill s WHERE s.id = :id AND (s.companyId = :companyId OR s.companyId IS NULL)")
    Optional<Skill> findByIdVisibleToCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Optional<Skill> findFirstByCompanyIdAndKeyOrderByVersionDesc(UUID companyId, String key);
}
