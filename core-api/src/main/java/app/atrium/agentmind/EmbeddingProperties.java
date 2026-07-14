package app.atrium.agentmind;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embeddings plumbing config (17 §config keys: EMBEDDINGS_PROVIDER, EMBEDDINGS_MODEL,
 * OPENAI_API_KEY, ATRIUM_MEMORY_TTL_DAYS). The API key is a secret: never logged.
 */
@ConfigurationProperties(prefix = "atrium.embeddings")
public record EmbeddingProperties(String provider, String model, Duration timeout, OpenAi openai) {

    public record OpenAi(String baseUrl, String apiKey) {}
}
