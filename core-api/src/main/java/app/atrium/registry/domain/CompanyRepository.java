package app.atrium.registry.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Companies ARE the tenants — the one repository without a companyId parameter. */
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findBySlug(String slug);
}
