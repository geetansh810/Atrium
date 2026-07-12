package app.atrium.execution;

import app.atrium.registry.ModelCatalogLookup;
import app.atrium.registry.domain.ModelCatalogEntry;
import org.springframework.stereotype.Service;

/**
 * usage_records.cost_micro_usd = tokens × catalog prices at call time (13 §1.2).
 * Prices are µUSD per MTok; result is µUSD, half-up per component.
 */
@Service
public class LlmCostCalculator {

    private static final long TOKENS_PER_MTOK = 1_000_000L;

    private final ModelCatalogLookup catalog;

    public LlmCostCalculator(ModelCatalogLookup catalog) {
        this.catalog = catalog;
    }

    public long costMicroUsd(String provider, String model, long tokensIn, long tokensOut) {
        ModelCatalogEntry entry = catalog.findEnabled(provider, model)
                .orElseThrow(() -> new IllegalStateException(
                        "(" + provider + ", " + model + ") vanished from model_catalog after a call"));
        return roundedProduct(tokensIn, entry.getPriceInMicroUsdPerMtok())
                + roundedProduct(tokensOut, entry.getPriceOutMicroUsdPerMtok());
    }

    private long roundedProduct(long tokens, long priceMicroUsdPerMtok) {
        return Math.round((double) tokens * priceMicroUsdPerMtok / TOKENS_PER_MTOK);
    }
}
