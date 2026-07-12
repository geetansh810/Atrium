package app.atrium.execution.spi;

import org.springframework.lang.Nullable;

/** One conversation turn. role: "system"|"user"|"assistant"|"tool". (13 §1.1, normative) */
public record LlmMessage(String role, String content, @Nullable String toolCallId) {

    public LlmMessage(String role, String content) {
        this(role, content, null);
    }
}
