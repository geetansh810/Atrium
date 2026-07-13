package app.atrium.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmMessage;
import app.atrium.execution.spi.LlmProvider;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import app.atrium.registry.ModelCatalogLookup;
import app.atrium.registry.domain.ModelCatalogEntry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Router contract: catalog validation, 13 §1.3 retry schedules, timeout. */
class LlmRouterTest {

    private static final LlmResult OK =
            new LlmResult("ok", List.of(), 10, 5, "end", "msg_1");

    private final ModelCatalogLookup catalog = mock(ModelCatalogLookup.class);

    private LlmRequest request(String provider) {
        return new LlmRequest(provider, "some-model", null,
                List.of(new LlmMessage("user", "hi")), List.of(), 64, null, null);
    }

    /** Zero-length backoffs so retry tests don't sleep. */
    private LlmRouter router(LlmProvider provider, Duration timeout) {
        when(catalog.findEnabled(anyString(), anyString()))
                .thenReturn(Optional.of(mock(ModelCatalogEntry.class)));
        LlmProperties properties = new LlmProperties(timeout,
                new LlmProperties.Retry(
                        List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO),
                        List.of(Duration.ZERO, Duration.ZERO)),
                new LlmProperties.Anthropic("http://unused", ""),
                new LlmProperties.Google("http://unused", ""));
        return new LlmRouter(List.of(provider), catalog, properties);
    }

    /** Provider that fails {@code failures} times with {@code kind}, then succeeds. */
    private static LlmProvider flaky(String id, LlmException.Kind kind, int failures,
                                     AtomicInteger calls) {
        return new LlmProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public LlmResult complete(LlmRequest r) throws LlmException {
                if (calls.incrementAndGet() <= failures) {
                    throw new LlmException(kind, "boom " + calls.get());
                }
                return OK;
            }
        };
    }

    @Test
    void unknownProviderIsUnknownModel() {
        LlmRouter router = router(flaky("fake", null, 0, new AtomicInteger()),
                Duration.ofSeconds(1));
        LlmException e = catchThrowableOfType(LlmException.class,
                () -> router.complete(request("openai")));
        assertThat(e.kind()).isEqualTo(LlmException.Kind.UNKNOWN_MODEL);
    }

    @Test
    void modelMissingFromCatalogIsUnknownModel() {
        LlmRouter router = router(flaky("fake", null, 0, new AtomicInteger()),
                Duration.ofSeconds(1));
        when(catalog.findEnabled(anyString(), anyString())).thenReturn(Optional.empty());
        LlmException e = catchThrowableOfType(LlmException.class,
                () -> router.complete(request("fake")));
        assertThat(e.kind()).isEqualTo(LlmException.Kind.UNKNOWN_MODEL);
    }

    @Test
    void rateLimitedRetriesThreeTimesThenThrows() {
        AtomicInteger calls = new AtomicInteger();
        LlmRouter router = router(
                flaky("fake", LlmException.Kind.RATE_LIMITED, Integer.MAX_VALUE, calls),
                Duration.ofSeconds(1));
        LlmException e = catchThrowableOfType(LlmException.class,
                () -> router.complete(request("fake")));
        assertThat(e.kind()).isEqualTo(LlmException.Kind.RATE_LIMITED);
        assertThat(calls.get()).as("1 initial + 3 retries").isEqualTo(4);
    }

    @Test
    void rateLimitedRecoversWithinTheAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LlmRouter router = router(flaky("fake", LlmException.Kind.RATE_LIMITED, 2, calls),
                Duration.ofSeconds(1));
        assertThat(router.complete(request("fake"))).isEqualTo(OK);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void providerDownRetriesTwiceThenThrows() {
        AtomicInteger calls = new AtomicInteger();
        LlmRouter router = router(
                flaky("fake", LlmException.Kind.PROVIDER_DOWN, Integer.MAX_VALUE, calls),
                Duration.ofSeconds(1));
        LlmException e = catchThrowableOfType(LlmException.class,
                () -> router.complete(request("fake")));
        assertThat(e.kind()).isEqualTo(LlmException.Kind.PROVIDER_DOWN);
        assertThat(calls.get()).as("1 initial + 2 retries").isEqualTo(3);
    }

    @Test
    void nonRetryableKindsFailFast() {
        for (LlmException.Kind kind : List.of(LlmException.Kind.CONTEXT_TOO_LONG,
                LlmException.Kind.CONTENT_FILTERED, LlmException.Kind.AUTH,
                LlmException.Kind.UNKNOWN_MODEL, LlmException.Kind.INVALID_REQUEST)) {
            AtomicInteger calls = new AtomicInteger();
            LlmRouter router = router(flaky("fake", kind, Integer.MAX_VALUE, calls),
                    Duration.ofSeconds(1));
            LlmException e = catchThrowableOfType(LlmException.class,
                    () -> router.complete(request("fake")));
            assertThat(e.kind()).isEqualTo(kind);
            assertThat(calls.get()).as("%s must not retry", kind).isEqualTo(1);
        }
    }

    @Test
    void timeoutMapsToProviderDown() {
        LlmProvider sleeper = new LlmProvider() {
            @Override
            public String id() {
                return "fake";
            }

            @Override
            public LlmResult complete(LlmRequest r) throws LlmException {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return OK;
            }
        };
        LlmRouter router = router(sleeper, Duration.ofMillis(50));
        LlmException e = catchThrowableOfType(LlmException.class,
                () -> router.complete(request("fake")));
        assertThat(e.kind()).isEqualTo(LlmException.Kind.PROVIDER_DOWN);
        assertThat(e.getMessage()).contains("timeout");
    }
}
