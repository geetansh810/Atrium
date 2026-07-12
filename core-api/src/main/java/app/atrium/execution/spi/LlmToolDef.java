package app.atrium.execution.spi;

import com.fasterxml.jackson.databind.JsonNode;

/** Tool offered to the model; inputSchema is full JSON Schema. (13 §1.1, normative) */
public record LlmToolDef(String name, String description, JsonNode inputSchema) {}
