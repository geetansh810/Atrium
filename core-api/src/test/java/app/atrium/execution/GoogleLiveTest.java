package app.atrium.execution;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.spi.LlmClient;
import app.atrium.execution.spi.LlmMessage;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import app.atrium.registry.domain.ModelCatalogEntry;
import app.atrium.registry.domain.ModelCatalogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real key, fast-tier Gemini model from the seeded catalog (V4), through the
 * full LlmClient path. Skipped when GOOGLE_API_KEY is absent — Gemini has a
 * genuine no-cost free tier, so this is the cheapest of the two live tests
 * to actually run.
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GoogleLiveTest extends IntegrationTestBase {

    @Autowired
    LlmClient llm;

    @Autowired
    ModelCatalogRepository catalog;

    @Test
    void fastTierModelReturnsTextAndNonzeroTokenCounts() throws Exception {
        ModelCatalogEntry fast = catalog.findByEnabledTrueOrderByProviderAscModelNameAsc().stream()
                .filter(e -> "google".equals(e.getProvider()) && "fast".equals(e.getTier()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no fast-tier google model seeded"));

        LlmResult result = llm.complete(new LlmRequest(
                "google", fast.getModelName(),
                "Answer in one short sentence.",
                List.of(new LlmMessage("user", "Say the word 'atrium' and nothing else.")),
                List.of(), 64, null, null));

        assertThat(result.content()).isNotBlank();
        assertThat(result.tokensIn()).isPositive();
        assertThat(result.tokensOut()).isPositive();
        assertThat(result.stopReason()).isEqualTo("end");
    }
}
