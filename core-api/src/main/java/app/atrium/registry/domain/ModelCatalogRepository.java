package app.atrium.registry.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Global catalog — deliberately unscoped (no company_id column, 15 §4.0). */
public interface ModelCatalogRepository extends JpaRepository<ModelCatalogEntry, UUID> {

    List<ModelCatalogEntry> findByEnabledTrueOrderByProviderAscModelNameAsc();

    Optional<ModelCatalogEntry> findByProviderAndModelName(String provider, String modelName);

    /** M-LN1: pick a cheap model for the same provider an agent already uses (14 §5). */
    Optional<ModelCatalogEntry> findFirstByProviderAndTierAndEnabledTrue(String provider, String tier);
}
