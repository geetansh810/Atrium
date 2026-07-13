package app.atrium.execution;

/**
 * Optional extra a provider bean implements so {@code LlmLoopRuntime}'s
 * pre-dispatch gate (12 §9, 13 §3.2 step 0) can check "is this provider even
 * configured" without dispatching a call — deliberately NOT part of the frozen
 * {@code LlmProvider} SPI (13 §1.1 is verbatim/normative). A provider that
 * doesn't implement this is assumed always ready.
 */
public interface ProviderReadiness {

    boolean isReady();
}
