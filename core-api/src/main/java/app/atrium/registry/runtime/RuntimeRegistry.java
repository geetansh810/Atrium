package app.atrium.registry.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Spring List&lt;AgentRuntime&gt; → map by type() (13 §3.1). Validates
 * agents.runtime_type + runtime_config on hire/update. Adding a runtime =
 * adding a bean; zero migrations, zero router edits.
 */
@Component
public class RuntimeRegistry {

    private final Map<String, AgentRuntime> runtimesByType;

    public RuntimeRegistry(List<AgentRuntime> runtimes) {
        this.runtimesByType = runtimes.stream()
                .collect(Collectors.toUnmodifiableMap(AgentRuntime::type, Function.identity()));
    }

    /** @throws ConfigException with field errors when type is unknown or config invalid */
    public void validate(String runtimeType, JsonNode runtimeConfig) {
        AgentRuntime runtime = runtimesByType.get(runtimeType);
        if (runtime == null) {
            throw new ConfigException(Map.of("runtimeType",
                    "Unknown runtime type '" + runtimeType + "'. Known: " + runtimesByType.keySet()));
        }
        runtime.validateConfig(runtimeConfig);
    }

    public AgentRuntime require(String runtimeType) {
        AgentRuntime runtime = runtimesByType.get(runtimeType);
        if (runtime == null) {
            throw new IllegalStateException("No runtime registered for type " + runtimeType);
        }
        return runtime;
    }
}
