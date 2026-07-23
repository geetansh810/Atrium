package app.atrium.execution.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row per LLM call that leaves the app, written best-effort at {@code
 * LlmRouter.complete} — the single doorway every provider goes through (see
 * V14 migration). Append-only: no setters, {@code created_at} is
 * {@code updatable=false}.
 *
 * <p>Distinct from {@code usage_records} (the billing ledger): this captures
 * the full request/response — system prompt, message history, tools, returned
 * text — for every call including ones that errored (which never bill) and the
 * background learning-extraction calls that carry no task.
 */
@Entity
@Table(name = "llm_request_logs")
public class LlmRequestLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "agent_id", updatable = false)
    private UUID agentId;

    @Column(name = "task_id", updatable = false)
    private UUID taskId;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(nullable = false, updatable = false)
    private String model;

    @Column(name = "system_prompt", updatable = false)
    private String systemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private JsonNode messages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private JsonNode tools;

    @Column(name = "response_text", updatable = false)
    private String responseText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", updatable = false)
    private JsonNode toolCalls;

    @Column(name = "tokens_in", updatable = false)
    private Long tokensIn;

    @Column(name = "tokens_out", updatable = false)
    private Long tokensOut;

    @Column(name = "stop_reason", updatable = false)
    private String stopReason;

    /** 'ok' | 'error'. */
    @Column(nullable = false, updatable = false)
    private String status;

    @Column(name = "error_kind", updatable = false)
    private String errorKind;

    @Column(name = "error_message", updatable = false)
    private String errorMessage;

    @Column(name = "latency_ms", nullable = false, updatable = false)
    private int latencyMs;

    @Column(name = "provider_request_id", updatable = false)
    private String providerRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LlmRequestLog() {}

    private LlmRequestLog(Builder b) {
        this.companyId = b.companyId;
        this.agentId = b.agentId;
        this.taskId = b.taskId;
        this.provider = b.provider;
        this.model = b.model;
        this.systemPrompt = b.systemPrompt;
        this.messages = b.messages;
        this.tools = b.tools;
        this.responseText = b.responseText;
        this.toolCalls = b.toolCalls;
        this.tokensIn = b.tokensIn;
        this.tokensOut = b.tokensOut;
        this.stopReason = b.stopReason;
        this.status = b.status;
        this.errorKind = b.errorKind;
        this.errorMessage = b.errorMessage;
        this.latencyMs = b.latencyMs;
        this.providerRequestId = b.providerRequestId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getAgentId() { return agentId; }
    public UUID getTaskId() { return taskId; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getSystemPrompt() { return systemPrompt; }
    public JsonNode getMessages() { return messages; }
    public JsonNode getTools() { return tools; }
    public String getResponseText() { return responseText; }
    public JsonNode getToolCalls() { return toolCalls; }
    public Long getTokensIn() { return tokensIn; }
    public Long getTokensOut() { return tokensOut; }
    public String getStopReason() { return stopReason; }
    public String getStatus() { return status; }
    public String getErrorKind() { return errorKind; }
    public String getErrorMessage() { return errorMessage; }
    public int getLatencyMs() { return latencyMs; }
    public String getProviderRequestId() { return providerRequestId; }
    public Instant getCreatedAt() { return createdAt; }

    public static final class Builder {
        private UUID companyId;
        private UUID agentId;
        private UUID taskId;
        private String provider;
        private String model;
        private String systemPrompt;
        private JsonNode messages;
        private JsonNode tools;
        private String responseText;
        private JsonNode toolCalls;
        private Long tokensIn;
        private Long tokensOut;
        private String stopReason;
        private String status;
        private String errorKind;
        private String errorMessage;
        private int latencyMs;
        private String providerRequestId;

        public Builder companyId(UUID v) { this.companyId = v; return this; }
        public Builder agentId(UUID v) { this.agentId = v; return this; }
        public Builder taskId(UUID v) { this.taskId = v; return this; }
        public Builder provider(String v) { this.provider = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder systemPrompt(String v) { this.systemPrompt = v; return this; }
        public Builder messages(JsonNode v) { this.messages = v; return this; }
        public Builder tools(JsonNode v) { this.tools = v; return this; }
        public Builder responseText(String v) { this.responseText = v; return this; }
        public Builder toolCalls(JsonNode v) { this.toolCalls = v; return this; }
        public Builder tokensIn(Long v) { this.tokensIn = v; return this; }
        public Builder tokensOut(Long v) { this.tokensOut = v; return this; }
        public Builder stopReason(String v) { this.stopReason = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder errorKind(String v) { this.errorKind = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public Builder latencyMs(int v) { this.latencyMs = v; return this; }
        public Builder providerRequestId(String v) { this.providerRequestId = v; return this; }

        public LlmRequestLog build() {
            return new LlmRequestLog(this);
        }
    }
}
