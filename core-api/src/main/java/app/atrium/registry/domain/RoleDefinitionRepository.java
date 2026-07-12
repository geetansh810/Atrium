package app.atrium.registry.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Company-scoped except for global templates (company_id IS NULL), which every tenant may read. */
public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, UUID> {

    @Query("SELECT r FROM RoleDefinition r WHERE r.companyId = :companyId OR r.companyId IS NULL "
            + "ORDER BY r.key, r.version")
    List<RoleDefinition> findVisibleToCompany(@Param("companyId") UUID companyId);

    /** Latest version of a global template. */
    Optional<RoleDefinition> findFirstByCompanyIdIsNullAndKeyOrderByVersionDesc(String key);

    /** Resolvable on hire: company's own definition or a global template. */
    @Query("SELECT r FROM RoleDefinition r WHERE r.id = :id AND (r.companyId = :companyId OR r.companyId IS NULL)")
    Optional<RoleDefinition> findByIdVisibleToCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Optional<RoleDefinition> findFirstByCompanyIdAndKeyOrderByVersionDesc(UUID companyId, String key);
}
