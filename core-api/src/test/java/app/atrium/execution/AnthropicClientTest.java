package app.atrium.execution;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import app.atrium.execution.spi.LlmException;
import app.atrium.execution.spi.LlmMessage;
import app.atrium.execution.spi.LlmRequest;
import app.atrium.execution.spi.LlmResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recorded-fixture tests (13 §1.2 checklist ④): happy path + EVERY row of the
 * 13 §1.3 error taxonomy maps to the right LlmException.Kind.
 */
class AnthropicClientTest {

    static WireMockServer wiremock;
    AnthropicClient client;
    ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void startServer() {
        wiremock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wiremock.start();
    }

    @AfterAll
    static void stopServer() {
        wiremock.stop();
    }

    @BeforeEach
    void reset() {
        wiremock.resetAll();
        client = new AnthropicClient(properties("sk-ant-test", wiremock.baseUrl()), json);
    }

    private static LlmProperties properties(String apiKey, String baseUrl) {
        return new LlmProperties(Duration.ofSeconds(5),
                new LlmProperties.Retry(List.of(), List.of()),
                new LlmProperties.Anthropic(baseUrl, apiKey));
    }

    private LlmRequest request() {
        return new LlmRequest("anthropic", "claude-haiku-4-5", "You are terse.",
                List.of(new LlmMessage("user", "Say hi")), List.of(), 64, null, null);
    }

    private void stubResponse(int status, String body) {
        wiremock.stubFor(post(urlEqualTo("/v1/messages")).willReturn(
                aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubError(int status, String errorType, String message) {
        stubResponse(status, """
                {"type":"error","error":{"type":"%s","message":"%s"}}
                """.formatted(errorType, message));
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void happyPathMapsTextUsageStopReasonAndRequestId() throws Exception {
        stubResponse(200, """
                {"id":"msg_abc123","type":"message","role":"assistant","model":"claude-haiku-4-5",
                 "content":[{"type":"text","text":"Hello from Atrium!"}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":17,"output_tokens":6}}
                """);

        LlmResult result = client.complete(request());

        assertThat(result.content()).isEqualTo("Hello from Atrium!");
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.tokensIn()).isEqualTo(17);
        assertThat(result.tokensOut()).isEqualTo(6);
        assertThat(result.stopReason()).isEqualTo("end");
        assertThat(result.providerRequestId()).isEqualTo("msg_abc123");
    }

    @Test
    void toolCallResponseMapsToLlmToolCalls() throws Exception {
        stubResponse(200, """
                {"id":"msg_tool","type":"message","role":"assistant","model":"claude-haiku-4-5",
                 "content":[{"type":"tool_use","id":"toolu_1","name":"get_weather",
                             "input":{"city":"Paris","unit":"celsius"}}],
                 "stop_reason":"tool_use","stop_sequence":null,
                 "usage":{"input_tokens":30,"output_tokens":12}}
                """);

        LlmResult result = client.complete(request());

        assertThat(result.content()).isNull();
        assertThat(result.stopReason()).isEqualTo("tool_use");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).id()).isEqualTo("toolu_1");
        assertThat(result.toolCalls().get(0).name()).isEqualTo("get_weather");
        assertThat(result.toolCalls().get(0).arguments().get("city").asText()).isEqualTo("Paris");
    }

    // ── 13 §1.3 taxonomy, row by row ────────────────────────────────────────

    @Test
    void rateLimit429MapsToRateLimited() {
        stubError(429, "rate_limit_error", "Number of requests has exceeded your rate limit");
        assertKind(LlmException.Kind.RATE_LIMITED);
    }

    @Test
    void serverErrorsMapToProviderDown() {
        stubError(500, "api_error", "Internal server error");
        assertKind(LlmException.Kind.PROVIDER_DOWN);

        wiremock.resetAll();
        stubError(529, "overloaded_error", "Overloaded");
        assertKind(LlmException.Kind.PROVIDER_DOWN);
    }

    @Test
    void connectionFailureMapsToProviderDown() {
        // point at a port with nothing listening
        client = new AnthropicClient(properties("sk-ant-test", "http://127.0.0.1:1"), json);
        assertKind(LlmException.Kind.PROVIDER_DOWN);
    }

    @Test
    void contextOverflow400MapsToContextTooLong() {
        stubError(400, "invalid_request_error",
                "prompt is too long: 250000 tokens > 200000 maximum");
        assertKind(LlmException.Kind.CONTEXT_TOO_LONG);
    }

    @Test
    void refusalStopReasonMapsToContentFiltered() {
        stubResponse(200, """
                {"id":"msg_ref","type":"message","role":"assistant","model":"claude-haiku-4-5",
                 "content":[],"stop_reason":"refusal","stop_sequence":null,
                 "usage":{"input_tokens":20,"output_tokens":0}}
                """);
        assertKind(LlmException.Kind.CONTENT_FILTERED);
    }

    @Test
    void badKey401MapsToAuth() {
        stubError(401, "authentication_error", "invalid x-api-key");
        assertKind(LlmException.Kind.AUTH);
    }

    @Test
    void missingKeyMapsToAuthWithoutAnyHttpCall() {
        client = new AnthropicClient(properties("", wiremock.baseUrl()), json);
        assertKind(LlmException.Kind.AUTH);
        assertThat(wiremock.getAllServeEvents()).isEmpty();
    }

    @Test
    void unknownModel404MapsToUnknownModel() {
        stubError(404, "not_found_error", "model: claude-nonexistent");
        assertKind(LlmException.Kind.UNKNOWN_MODEL);
    }

    @Test
    void otherBadRequestMapsToInvalidRequest() {
        stubError(400, "invalid_request_error", "messages: roles must alternate");
        assertKind(LlmException.Kind.INVALID_REQUEST);
    }

    private void assertKind(LlmException.Kind expected) {
        LlmException e = catchThrowableOfType(LlmException.class, () -> client.complete(request()));
        assertThat(e).as("expected LlmException(%s)", expected).isNotNull();
        assertThat(e.kind()).isEqualTo(expected);
    }

    // ── secrets never meet prompts: exception text carries no key material ──

    @Test
    void exceptionMessagesNeverContainTheApiKey() {
        stubError(401, "authentication_error", "invalid x-api-key");
        LlmException e = catchThrowableOfType(LlmException.class, () -> client.complete(request()));
        assertThat(e.getMessage()).doesNotContain("sk-ant-test");
    }
}
