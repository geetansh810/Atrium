package app.atrium.execution;

import app.atrium.execution.spi.LlmException;

/** 13 §1.3 taxonomy → the flag reason string written to task_events/outbox. */
final class FlagReasons {

    private FlagReasons() {}

    static String forKind(LlmException.Kind kind) {
        return switch (kind) {
            case RATE_LIMITED -> "provider_rate_limited";
            case PROVIDER_DOWN -> "provider_unavailable";
            case CONTEXT_TOO_LONG -> "context_too_long";
            case CONTENT_FILTERED -> "content_filtered";
            case AUTH, UNKNOWN_MODEL -> "config_incomplete";
            case INVALID_REQUEST -> "execution_error";
        };
    }
}
