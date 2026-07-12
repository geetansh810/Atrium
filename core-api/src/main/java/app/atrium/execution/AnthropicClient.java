package app.atrium.execution;

import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmMessage;
import app.atrium.execution.spi.LlmProvider;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import app.atrium.execution.spi.LlmToolCall;
import app.atrium.execution.spi.LlmToolDef;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * The Anthropic adapter behind the SPI (13 §1.2 checklist ①): official Java SDK,
 * key from ANTHROPIC_API_KEY only (08 §Security). SDK-level retries are disabled —
 * the 13 §1.3 retry policy lives in LlmRouter, and nowhere else.
 *
 * <p>A missing key doesn't fail startup: complete() throws AUTH, which the
 * pre-dispatch gate (12 §9, M0.5b) turns into a config_incomplete flag.
 */
@Component
public class AnthropicClient implements LlmProvider {

    private final ObjectMapper objectMapper;

    @Nullable
    private final com.anthropic.client.AnthropicClient sdk;

    public AnthropicClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String apiKey = properties.anthropic().apiKey();
        this.sdk = (apiKey == null || apiKey.isBlank()) ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        .baseUrl(properties.anthropic().baseUrl())
                        .timeout(properties.timeout())
                        .maxRetries(0)      // taxonomy owns retries (LlmRouter)
                        .build();
    }

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public LlmResult complete(LlmRequest request) throws LlmException {
        if (sdk == null) {
            throw new LlmException(LlmException.Kind.AUTH, "ANTHROPIC_API_KEY is not configured");
        }
        MessageCreateParams params = toParams(request);
        Message message;
        try {
            message = sdk.messages().create(params);
        } catch (RateLimitException e) {
            throw new LlmException(LlmException.Kind.RATE_LIMITED, "Anthropic rate limit", e);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new LlmException(LlmException.Kind.AUTH, "Anthropic rejected the API key", e);
        } catch (NotFoundException e) {
            throw new LlmException(LlmException.Kind.UNKNOWN_MODEL,
                    "Anthropic does not know model '" + request.model() + "'", e);
        } catch (BadRequestException | UnprocessableEntityException e) {
            String detail = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            if (detail.contains("too long") || detail.contains("context window")
                    || detail.contains("too many tokens")) {
                throw new LlmException(LlmException.Kind.CONTEXT_TOO_LONG,
                        "Input exceeds the model's context window", e);
            }
            throw new LlmException(LlmException.Kind.INVALID_REQUEST,
                    "Anthropic rejected the request: " + e.getMessage(), e);
        } catch (InternalServerException e) {
            throw new LlmException(LlmException.Kind.PROVIDER_DOWN,
                    "Anthropic server error", e);
        } catch (AnthropicIoException e) {
            throw new LlmException(LlmException.Kind.PROVIDER_DOWN,
                    "Could not reach Anthropic", e);
        } catch (RuntimeException e) {
            throw new LlmException(LlmException.Kind.INVALID_REQUEST,
                    "Unexpected Anthropic SDK failure: " + e.getMessage(), e);
        }
        return toResult(message);
    }

    // ── request mapping ─────────────────────────────────────────────────────

    private MessageCreateParams toParams(LlmRequest request) throws LlmException {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(request.maxOutputTokens());
        if (request.system() != null) {
            builder.system(request.system());
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        for (LlmMessage message : request.messages()) {
            switch (message.role()) {
                case "user" -> builder.addUserMessage(message.content());
                case "assistant" -> builder.addMessage(MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .content(message.content())
                        .build());
                case "tool" -> builder.addMessage(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(List.of(
                                ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                        .toolUseId(message.toolCallId())
                                        .content(message.content())
                                        .build())))
                        .build());
                case "system" -> builder.system(message.content());
                default -> throw new LlmException(LlmException.Kind.INVALID_REQUEST,
                        "Unknown message role '" + message.role() + "'");
            }
        }
        for (LlmToolDef tool : request.tools()) {
            builder.addTool(toTool(tool));
        }
        if (request.jsonSchema() != null) {
            builder.outputConfig(OutputConfig.builder()
                    .format(JsonOutputFormat.builder()
                            .schema(toOutputSchema(request.jsonSchema()))
                            .build())
                    .build());
        }
        return builder.build();
    }

    /** LlmToolDef.inputSchema is a full JSON Schema; the SDK wants its parts. */
    private Tool toTool(LlmToolDef def) {
        Tool.InputSchema.Builder schema = Tool.InputSchema.builder();
        JsonNode properties = def.inputSchema().get("properties");
        if (properties != null) {
            Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
            properties.fields().forEachRemaining(field ->
                    props.putAdditionalProperty(field.getKey(), toJsonValue(field.getValue())));
            schema.properties(props.build());
        }
        JsonNode required = def.inputSchema().get("required");
        if (required != null && required.isArray()) {
            List<String> names = new ArrayList<>();
            required.forEach(n -> names.add(n.asText()));
            schema.required(names);
        }
        return Tool.builder()
                .name(def.name())
                .description(def.description())
                .inputSchema(schema.build())
                .build();
    }

    private JsonOutputFormat.Schema toOutputSchema(JsonNode schemaNode) {
        JsonOutputFormat.Schema.Builder builder = JsonOutputFormat.Schema.builder();
        schemaNode.fields().forEachRemaining(field ->
                builder.putAdditionalProperty(field.getKey(), toJsonValue(field.getValue())));
        return builder.build();
    }

    private JsonValue toJsonValue(JsonNode node) {
        return JsonValue.from(objectMapper.convertValue(node, Object.class));
    }

    // ── response mapping ────────────────────────────────────────────────────

    private LlmResult toResult(Message message) throws LlmException {
        StringBuilder text = new StringBuilder();
        List<LlmToolCall> toolCalls = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(toolUse -> toolCalls.add(new LlmToolCall(
                    toolUse.id(), toolUse.name(), toJsonNode(toolUse._input()))));
        }

        String rawStop = message.stopReason().map(Object::toString).orElse("end_turn");
        String stopReason = switch (rawStop) {
            case "max_tokens", "model_context_window_exceeded" -> "max_tokens";
            case "tool_use" -> "tool_use";
            case "refusal" -> throw new LlmException(LlmException.Kind.CONTENT_FILTERED,
                    "Anthropic refused the request (stop_reason=refusal)");
            default -> "end";       // end_turn, stop_sequence, pause_turn
        };

        return new LlmResult(
                text.isEmpty() ? null : text.toString(),
                List.copyOf(toolCalls),
                message.usage().inputTokens(),
                message.usage().outputTokens(),
                stopReason,
                message.id());
    }

    private JsonNode toJsonNode(JsonValue value) {
        return objectMapper.valueToTree(value);
    }
}
