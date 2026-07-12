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
 * M0.5a Done-when: real key, tier=fast model from the seeded catalog, through
 * the full LlmClient path. Skipped in CI when ANTHROPIC_API_KEY is absent.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicLiveTest extends IntegrationTestBase {

    @Autowired
    LlmClient llm;

    @Autowired
    ModelCatalogRepository catalog;

    @Test
    void fastTierModelReturnsTextAndNonzeroTokenCounts() throws Exception {
        ModelCatalogEntry fast = catalog.findByEnabledTrueOrderByProviderAscModelNameAsc().stream()
                .filter(e -> "anthropic".equals(e.getProvider()) && "fast".equals(e.getTier()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no fast-tier anthropic model seeded"));

        LlmResult result = llm.complete(new LlmRequest(
                "anthropic", fast.getModelName(),
                "Answer in one short sentence.",
                List.of(new LlmMessage("user", "Say the word 'atrium' and nothing else.")),
                List.of(), 64, null, null));

        assertThat(result.content()).isNotBlank();
        assertThat(result.tokensIn()).isPositive();
        assertThat(result.tokensOut()).isPositive();
        assertThat(result.stopReason()).isEqualTo("end");
        assertThat(result.providerRequestId()).isNotBlank();
    }
}
