package app.atrium.execution.spi;

/**
 * The error taxonomy (13 §1.3, normative) — every provider adapter maps its SDK
 * errors into exactly these kinds. The runner (M0.5b) maps kinds to flag
 * reasons; the router owns the in-attempt retry policy.
 */
public class LlmException extends Exception {

    public enum Kind {
        /** 429/quota — retry same attempt 3× (2s/8s/30s), then flag provider_rate_limited. */
        RATE_LIMITED,
        /** 5xx/timeout/connect — retry 2× (5s/20s), then flag provider_unavailable. */
        PROVIDER_DOWN,
        /** input over window — no retry → flag context_too_long. */
        CONTEXT_TOO_LONG,
        /** provider refusal — no retry → flag content_filtered. */
        CONTENT_FILTERED,
        /** bad/missing key — no retry → flag config_incomplete, pause the agent's loop (12 §9). */
        AUTH,
        /** not in catalog / provider rejects — no retry → flag config_incomplete. */
        UNKNOWN_MODEL,
        /** our bug — no retry → flag execution_error, log at ERROR. */
        INVALID_REQUEST
    }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
