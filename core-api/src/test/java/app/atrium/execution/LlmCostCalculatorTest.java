package app.atrium.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.atrium.registry.ModelCatalogLookup;
import app.atrium.registry.domain.ModelCatalogEntry;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LlmCostCalculatorTest {

    private final ModelCatalogLookup catalog = mock(ModelCatalogLookup.class);
    private final LlmCostCalculator calculator = new LlmCostCalculator(catalog);

    @Test
    void costIsTokensTimesCatalogPrices() {
        // claude-sonnet-5 seed prices: 3M µUSD/MTok in, 15M µUSD/MTok out
        ModelCatalogEntry entry = mock(ModelCatalogEntry.class);
        when(entry.getPriceInMicroUsdPerMtok()).thenReturn(3_000_000L);
        when(entry.getPriceOutMicroUsdPerMtok()).thenReturn(15_000_000L);
        when(catalog.findEnabled("anthropic", "claude-sonnet-5")).thenReturn(Optional.of(entry));

        // 10_000 in → 30_000 µUSD; 2_000 out → 30_000 µUSD
        assertThat(calculator.costMicroUsd("anthropic", "claude-sonnet-5", 10_000, 2_000))
                .isEqualTo(60_000L);
        // tiny call still rounds sensibly: 17 in → 51 µUSD, 6 out → 90 µUSD
        assertThat(calculator.costMicroUsd("anthropic", "claude-sonnet-5", 17, 6))
                .isEqualTo(141L);
    }

    @Test
    void missingCatalogRowIsALoudError() {
        when(catalog.findEnabled("anthropic", "ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> calculator.costMicroUsd("anthropic", "ghost", 1, 1))
                .isInstanceOf(IllegalStateException.class);
    }
}
