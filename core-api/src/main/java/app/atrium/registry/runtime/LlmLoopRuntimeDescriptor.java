package app.atrium.registry.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Descriptor-only registration of the built-in llm_loop runtime: validateConfig
 * is real, start/stop/health are inert until the actual loop lands at M0.5b in
 * execution/ (which replaces this bean).
 *
 * <p>runtime_config schema (13 §3.2): {pollSeconds?, maxToolTurns?,
 * maxAttemptsPerTask?, contextBudgetTokens?} — all optional positive integers.
 */
@Component
public class LlmLoopRuntimeDescriptor implements AgentRuntime {

    private static final Set<String> ALLOWED_KEYS =
            Set.of("pollSeconds", "maxToolTurns", "maxAttemptsPerTask", "contextBudgetTokens");

    @Override
    public String type() {
        return "llm_loop";
    }

    @Override
    public void validateConfig(JsonNode runtimeConfig) {
        if (runtimeConfig == null || runtimeConfig.isNull()) {
            return; // defaults apply
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (!runtimeConfig.isObject()) {
            throw new ConfigException(Map.of("runtimeConfig", "must be a JSON object"));
        }
        for (Iterator<String> it = runtimeConfig.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            if (!ALLOWED_KEYS.contains(field)) {
                errors.put("runtimeConfig." + field, "unknown key; allowed: " + ALLOWED_KEYS);
                continue;
            }
            JsonNode value = runtimeConfig.get(field);
            if (!value.isIntegralNumber() || value.asLong() <= 0) {
                errors.put("runtimeConfig." + field, "must be a positive integer");
            }
        }
        if (!errors.isEmpty()) {
            throw new ConfigException(errors);
        }
    }

    @Override
    public void start(AgentHandle agent) {
        // no runtime yet — M0.5b
    }

    @Override
    public void stop(AgentHandle agent) {
        // no runtime yet — M0.5b
    }

    @Override
    public RuntimeHealth health(AgentHandle agent) {
        return RuntimeHealth.stopped();
    }
}
