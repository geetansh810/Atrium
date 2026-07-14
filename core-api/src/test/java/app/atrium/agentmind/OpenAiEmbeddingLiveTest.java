package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 13 §2 Done-when analog: a real key, real model, through the full
 * EmbeddingClient path. Skipped in CI when OPENAI_API_KEY is absent.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiEmbeddingLiveTest {

    @Test
    void realKeyReturnsA1536DimensionVector() {
        EmbeddingProperties properties = new EmbeddingProperties("openai", "text-embedding-3-small",
                Duration.ofSeconds(10),
                new EmbeddingProperties.OpenAi("https://api.openai.com", System.getenv("OPENAI_API_KEY")));
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(properties, new ObjectMapper());

        float[] vector = client.embed("Atrium is a multi-tenant AI employee platform.");

        assertThat(vector).hasSize(1536);
    }
}
