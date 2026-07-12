package app.atrium.execution;

import app.atrium.execution.spi.LlmClient;
import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmProvider;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import app.atrium.registry.ModelCatalogLookup;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only LlmClient (13 §1.1): routes by {@code request.provider()}, validates
 * (provider, model) against the catalog, enforces the per-call timeout, and owns
 * the in-attempt retry policy from the 13 §1.3 taxonomy. Retries stay within one
 * task attempt — usage is recorded once per successful call (M0.5b), so a retried
 * transport call that never returned bills nothing.
 */
@Service
public class LlmRouter implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private final Map<String, LlmProvider> providers;
    private final ModelCatalogLookup catalog;
    private final LlmProperties properties;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public LlmRouter(List<LlmProvider> providers, ModelCatalogLookup catalog,
                     LlmProperties properties) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::id, Function.identity()));
        this.catalog = catalog;
        this.properties = properties;
    }

    @Override
    public LlmResult complete(LlmRequest request) throws LlmException {
        LlmProvider provider = providers.get(request.provider());
        if (provider == null) {
            throw new LlmException(LlmException.Kind.UNKNOWN_MODEL,
                    "No LlmProvider registered for '" + request.provider() + "'");
        }
        catalog.findEnabled(request.provider(), request.model())
                .orElseThrow(() -> new LlmException(LlmException.Kind.UNKNOWN_MODEL,
                        "(" + request.provider() + ", " + request.model()
                                + ") is not enabled in model_catalog"));

        int retriesUsed = 0;
        while (true) {
            try {
                return callWithTimeout(provider, request);
            } catch (LlmException e) {
                List<Duration> backoff = backoffFor(e.kind());
                if (retriesUsed >= backoff.size()) {
                    throw e;
                }
                Duration delay = backoff.get(retriesUsed++);
                log.warn("LLM call to {}/{} failed with {} — retry {}/{} in {}",
                        request.provider(), request.model(), e.kind(),
                        retriesUsed, backoff.size(), delay);
                sleep(delay);
            }
        }
    }

    /** 13 §1.3 retry schedules; everything else fails fast. */
    private List<Duration> backoffFor(LlmException.Kind kind) {
        return switch (kind) {
            case RATE_LIMITED -> properties.retry().rateLimitedBackoff();
            case PROVIDER_DOWN -> properties.retry().providerDownBackoff();
            default -> List.of();
        };
    }

    private LlmResult callWithTimeout(LlmProvider provider, LlmRequest request)
            throws LlmException {
        Future<LlmResult> future = executor.submit(() -> provider.complete(request));
        try {
            return future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new LlmException(LlmException.Kind.PROVIDER_DOWN,
                    "LLM call exceeded the " + properties.timeout() + " timeout", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof LlmException llmException) {
                throw llmException;
            }
            log.error("Provider {} threw outside the taxonomy", provider.id(), e.getCause());
            throw new LlmException(LlmException.Kind.INVALID_REQUEST,
                    "Unexpected provider failure: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.PROVIDER_DOWN, "LLM call interrupted", e);
        }
    }

    private void sleep(Duration duration) throws LlmException {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.PROVIDER_DOWN, "Retry backoff interrupted", e);
        }
    }
}
