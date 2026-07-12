package app.atrium.execution.spi;

import java.util.List;
import org.springframework.lang.Nullable;

/** (13 §1.1, normative) */
public record LlmResult(
        @Nullable String content,        // assistant text (null if pure tool call)
        List<LlmToolCall> toolCalls,     // empty if none
        long tokensIn, long tokensOut,   // ALWAYS present — accounting depends on it
        String stopReason,               // 'end'|'max_tokens'|'tool_use'|'filtered'
        String providerRequestId         // provider-side id for tracing; "" if absent
) {}
