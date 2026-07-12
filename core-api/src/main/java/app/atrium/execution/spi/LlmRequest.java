package app.atrium.execution.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.lang.Nullable;

/** (13 §1.1, normative) */
public record LlmRequest(
        String provider,            // 'anthropic'|'openai'|'google'|… (agents.model_provider)
        String model,               // agents.model_name — must exist in model_catalog
        @Nullable String system,    // system prompt (kept separate; providers map internally)
        List<LlmMessage> messages,
        List<LlmToolDef> tools,     // empty = none
        int maxOutputTokens,
        @Nullable Double temperature,
        @Nullable JsonNode jsonSchema   // non-null => structured output requested
) {}
