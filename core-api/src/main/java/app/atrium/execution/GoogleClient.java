package app.atrium.execution;

import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmMessage;
import app.atrium.execution.spi.LlmProvider;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import app.atrium.execution.spi.LlmToolCall;
import app.atrium.execution.spi.LlmToolDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The Google Gemini adapter behind the SPI (13 §1.2 checklist ①). No official
 * Google Gemini Java SDK dependency exists in this project yet, so this talks
 * REST directly (06 §C explicitly allows "official Java or REST clients") via
 * Spring's {@code RestClient} — already a transitive dependency of
 * spring-boot-starter-web, nothing new to add. Auth via the {@code x-goog-api-key}
 * header (never a query param, so the key never lands in a URL/access log).
 *
 * <p>A missing key doesn't fail startup: complete() throws AUTH, same contract
 * as {@link AnthropicClient}.
 */
@Component
public class GoogleClient implements LlmProvider, ProviderReadiness {

    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Nullable
    private final RestClient sdk;

    public GoogleClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.apiKey = properties.google().apiKey();
        this.sdk = (apiKey == null || apiKey.isBlank()) ? null
                : RestClient.builder()
                        .baseUrl(properties.google().baseUrl())
                        .requestFactory(clientHttpRequestFactory(properties.timeout()))
                        .build();
    }

    private static ClientHttpRequestFactory clientHttpRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }

    @Override
    public String id() {
        return "google";
    }

    /** Pre-dispatch gate hook (12 §9) — false means never dispatch, flag/park instead. */
    @Override
    public boolean isReady() {
        return sdk != null;
    }

    @Override
    public LlmResult complete(LlmRequest request) throws LlmException {
        if (sdk == null) {
            throw new LlmException(LlmException.Kind.AUTH, "GOOGLE_API_KEY is not configured");
        }
        ObjectNode body = toRequestBody(request);
        JsonNode response;
        try {
            response = sdk.post()
                    .uri("/v1beta/models/{model}:generateContent", request.model())
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw mapHttpError(e, request.model());
        } catch (RuntimeException e) {
            // RestClient wraps connect/timeout failures in ResourceAccessException,
            // any other transport failure lands here too — all "couldn't reach it".
            throw new LlmException(LlmException.Kind.PROVIDER_DOWN,
                    "Could not reach Google Gemini: " + e.getMessage(), e);
        }
        return toResult(response);
    }

    private LlmException mapHttpError(RestClientResponseException e, String model) {
        int status = e.getStatusCode().value();
        String message = errorMessage(e);
        return switch (status) {
            case 429 -> new LlmException(LlmException.Kind.RATE_LIMITED, "Gemini rate limit: " + message, e);
            case 401, 403 -> new LlmException(LlmException.Kind.AUTH, "Gemini rejected the API key", e);
            case 404 -> new LlmException(LlmException.Kind.UNKNOWN_MODEL,
                    "Gemini does not know model '" + model + "'", e);
            case 400 -> {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("token") && (lower.contains("exceed") || lower.contains("too long")
                        || lower.contains("context"))) {
                    yield new LlmException(LlmException.Kind.CONTEXT_TOO_LONG,
                            "Input exceeds the model's context window", e);
                }
                yield new LlmException(LlmException.Kind.INVALID_REQUEST,
                        "Gemini rejected the request: " + message, e);
            }
            default -> {
                if (status >= 500) {
                    yield new LlmException(LlmException.Kind.PROVIDER_DOWN, "Gemini server error", e);
                }
                yield new LlmException(LlmException.Kind.INVALID_REQUEST,
                        "Unexpected Gemini response " + status + ": " + message, e);
            }
        };
    }

    private String errorMessage(RestClientResponseException e) {
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsByteArray());
            JsonNode message = body.path("error").path("message");
            return message.isMissingNode() ? e.getMessage() : message.asText();
        } catch (Exception parseFailure) {
            return e.getMessage();
        }
    }

    // ── request mapping ─────────────────────────────────────────────────────

    private ObjectNode toRequestBody(LlmRequest request) throws LlmException {
        ObjectNode body = objectMapper.createObjectNode();

        if (request.system() != null) {
            ObjectNode systemInstruction = body.putObject("systemInstruction");
            systemInstruction.putArray("parts").addObject().put("text", request.system());
        }

        ArrayNode contents = body.putArray("contents");
        for (LlmMessage message : request.messages()) {
            contents.add(toContent(message));
        }

        if (!request.tools().isEmpty()) {
            ArrayNode functionDeclarations = body.putArray("tools")
                    .addObject().putArray("functionDeclarations");
            for (LlmToolDef tool : request.tools()) {
                ObjectNode decl = functionDeclarations.addObject();
                decl.put("name", tool.name());
                decl.put("description", tool.description());
                decl.set("parameters", toParametersSchema(tool.inputSchema()));
            }
        }

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        if (request.temperature() != null) {
            generationConfig.put("temperature", request.temperature());
        }
        if (request.jsonSchema() != null) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.set("responseSchema", request.jsonSchema());
        }
        return body;
    }

    /**
     * Gemini's {@code parameters} field is an OpenAPI 3.0 schema subset, not full
     * JSON Schema — passing {@code inputSchema} through verbatim risks a 400 on
     * keywords it doesn't recognize (e.g. {@code $schema}). Mirrors
     * AnthropicClient.toTool: keep only {@code type}/{@code properties}/{@code required}.
     */
    private ObjectNode toParametersSchema(JsonNode inputSchema) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", inputSchema.path("type").asText("object"));
        JsonNode properties = inputSchema.get("properties");
        if (properties != null) {
            schema.set("properties", properties);
        }
        JsonNode required = inputSchema.get("required");
        if (required != null && required.isArray()) {
            schema.set("required", required);
        }
        return schema;
    }

    /** Gemini roles: "user"|"model" only — assistant→model, tool results ride back as role "user". */
    private ObjectNode toContent(LlmMessage message) throws LlmException {
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = content.putArray("parts");
        switch (message.role()) {
            case "user", "system" -> {
                content.put("role", "user");
                parts.addObject().put("text", message.content());
            }
            case "assistant" -> {
                content.put("role", "model");
                parts.addObject().put("text", message.content());
            }
            case "tool" -> {
                content.put("role", "user");
                ObjectNode functionResponse = parts.addObject().putObject("functionResponse");
                // LlmMessage carries no function name for tool results (13 §1.1) — Gemini
                // requires one, so the toolCallId (our LlmToolCall.id, opaque to us) doubles
                // as the name; it round-trips fine since Gemini only echoes it back to us.
                functionResponse.put("name", message.toolCallId());
                functionResponse.putObject("response").put("result", message.content());
            }
            default -> throw new LlmException(LlmException.Kind.INVALID_REQUEST,
                    "Unknown message role '" + message.role() + "'");
        }
        return content;
    }

    // ── response mapping ────────────────────────────────────────────────────

    private LlmResult toResult(JsonNode response) throws LlmException {
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            // e.g. the whole prompt was blocked before any candidate was produced
            String blockReason = response.path("promptFeedback").path("blockReason").asText("unspecified");
            throw new LlmException(LlmException.Kind.CONTENT_FILTERED,
                    "Gemini returned no candidates (blockReason=" + blockReason + ")");
        }

        StringBuilder text = new StringBuilder();
        List<LlmToolCall> toolCalls = new ArrayList<>();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.has("text")) {
                text.append(part.get("text").asText());
            }
            if (part.has("functionCall")) {
                JsonNode call = part.get("functionCall");
                String id = call.has("id") ? call.get("id").asText() : UUID.randomUUID().toString();
                toolCalls.add(new LlmToolCall(id, call.get("name").asText(), call.path("args")));
            }
        }

        String finishReason = candidate.path("finishReason").asText("STOP");
        String stopReason = switch (finishReason) {
            case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT" -> throw new LlmException(
                    LlmException.Kind.CONTENT_FILTERED, "Gemini refused the request (finishReason=" + finishReason + ")");
            case "MAX_TOKENS" -> "max_tokens";
            default -> toolCalls.isEmpty() ? "end" : "tool_use";
        };

        JsonNode usage = response.path("usageMetadata");
        return new LlmResult(
                text.isEmpty() ? null : text.toString(),
                List.copyOf(toolCalls),
                usage.path("promptTokenCount").asLong(0),
                usage.path("candidatesTokenCount").asLong(0),
                stopReason,
                response.path("responseId").asText(""));
    }
}
