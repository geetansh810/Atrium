package app.atrium.execution.spi;

import com.fasterxml.jackson.databind.JsonNode;

/** A tool call the model requested. (13 §1.1, normative) */
public record LlmToolCall(String id, String name, JsonNode arguments) {}
