package app.atrium.registry;

import app.atrium.registry.domain.ModelCatalogEntry;
import app.atrium.registry.domain.ModelCatalogRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaModelCatalogLookup implements ModelCatalogLookup {

    private final ModelCatalogRepository catalog;

    public JpaModelCatalogLookup(ModelCatalogRepository catalog) {
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ModelCatalogEntry> findEnabled(String provider, String modelName) {
        return catalog.findByProviderAndModelName(provider, modelName)
                .filter(ModelCatalogEntry::isEnabled);
    }
}
