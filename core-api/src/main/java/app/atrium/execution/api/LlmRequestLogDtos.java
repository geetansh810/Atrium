package app.atrium.execution.api;

import app.atrium.execution.domain.LlmRequestLog;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public final class LlmRequestLogDtos {

    private LlmRequestLogDtos() {}

    /** Compact row for the log list — no large prompt bodies (fetch those via the detail endpoint). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LogRow(UUID id, UUID agentId, UUID taskId, String provider, String model,
                         Long tokensIn, Long tokensOut, String stopReason, String status,
                         String errorKind, int latencyMs, Instant createdAt) {
        public static LogRow from(LlmRequestLog r) {
            return new LogRow(r.getId(), r.getAgentId(), r.getTaskId(), r.getProvider(), r.getModel(),
                    r.getTokensIn(), r.getTokensOut(), r.getStopReason(), r.getStatus(),
                    r.getErrorKind(), r.getLatencyMs(), r.getCreatedAt());
        }
    }

    /** Full record including the exact prompt sent and text returned. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LogDetail(UUID id, UUID agentId, UUID taskId, String provider, String model,
                            String systemPrompt, JsonNode messages, JsonNode tools,
                            String responseText, JsonNode toolCalls, Long tokensIn, Long tokensOut,
                            String stopReason, String status, String errorKind, String errorMessage,
                            int latencyMs, String providerRequestId, Instant createdAt) {
        public static LogDetail from(LlmRequestLog r) {
            return new LogDetail(r.getId(), r.getAgentId(), r.getTaskId(), r.getProvider(), r.getModel(),
                    r.getSystemPrompt(), r.getMessages(), r.getTools(), r.getResponseText(),
                    r.getToolCalls(), r.getTokensIn(), r.getTokensOut(), r.getStopReason(),
                    r.getStatus(), r.getErrorKind(), r.getErrorMessage(), r.getLatencyMs(),
                    r.getProviderRequestId(), r.getCreatedAt());
        }
    }
}
