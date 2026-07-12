package app.atrium.execution;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM plumbing config (17 §config keys: ATRIUM_LLM_TIMEOUT, ANTHROPIC_API_KEY).
 * Retry backoffs are the 13 §1.3 schedules — overridable so tests don't sleep.
 * The API key is a secret: never log it, never let it near a prompt (08 §Security).
 */
@ConfigurationProperties(prefix = "atrium.llm")
public record LlmProperties(Duration timeout, Retry retry, Anthropic anthropic) {

    public record Retry(List<Duration> rateLimitedBackoff, List<Duration> providerDownBackoff) {}

    public record Anthropic(String baseUrl, String apiKey) {}
}
