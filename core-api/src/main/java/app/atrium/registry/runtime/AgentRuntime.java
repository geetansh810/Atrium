package app.atrium.registry.runtime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Any agent type — the runtime axis of pluggability (13 §3.1, normative).
 * TEXT column + code validation, deliberately NOT a DB CHECK, so a new runtime
 * is zero-migration. Implementations live in execution/ (LlmLoopRuntime at
 * M0.5b); registry owns the SPI because hire/patch validates against it.
 */
public interface AgentRuntime {

    /** 'llm_loop' | 'webhook' | … = agents.runtime_type */
    String type();

    /** Begin/resume the agent's work loop. */
    void start(AgentHandle agent);

    /** Graceful: finish current task step, release. */
    void stop(AgentHandle agent);

    RuntimeHealth health(AgentHandle agent);

    /** Throws ConfigException → hire/patch 400s with field errors. */
    void validateConfig(JsonNode runtimeConfig);
}
