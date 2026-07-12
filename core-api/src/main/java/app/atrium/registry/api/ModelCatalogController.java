package app.atrium.registry.api;

import app.atrium.registry.api.ModelCatalogDtos.ModelCatalogResponse;
import app.atrium.registry.domain.ModelCatalogRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model-catalog")
public class ModelCatalogController {

    private final ModelCatalogRepository modelCatalog;

    public ModelCatalogController(ModelCatalogRepository modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    /** Enabled models only; prices omitted (16 §1 — admin-only in v1). */
    @GetMapping
    public List<ModelCatalogResponse> list() {
        return modelCatalog.findByEnabledTrueOrderByProviderAscModelNameAsc().stream()
                .map(ModelCatalogResponse::from)
                .toList();
    }
}
