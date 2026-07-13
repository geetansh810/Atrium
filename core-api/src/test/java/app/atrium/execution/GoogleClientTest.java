package app.atrium.execution;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
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
 * Recorded-fixture tests (13 §1.2 checklist ④), mirroring AnthropicClientTest:
 * happy path + every row of the 13 §1.3 error taxonomy maps to the right
 * LlmException.Kind for the Gemini REST adapter.
 */
class GoogleClientTest {

    static WireMockServer wiremock;
    GoogleClient client;
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
        client = new GoogleClient(properties("goog-test-key", wiremock.baseUrl()), json);
    }

    private static LlmProperties properties(String apiKey, String baseUrl) {
        return new LlmProperties(Duration.ofSeconds(5),
                new LlmProperties.Retry(List.of(), List.of()),
                new LlmProperties.Anthropic("http://unused", ""),
                new LlmProperties.Google(baseUrl, apiKey));
    }

    private LlmRequest request() {
        return new LlmRequest("google", "gemini-3.1-flash-lite", "You are terse.",
                List.of(new LlmMessage("user", "Say hi")), List.of(), 64, null, null);
    }

    private static final String PATH = "/v1beta/models/gemini-3.1-flash-lite:generateContent";

    private void stubResponse(int status, String body) {
        wiremock.stubFor(post(urlEqualTo(PATH)).willReturn(
                aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubError(int status, String message) {
        stubResponse(status, """
                {"error":{"code":%d,"message":"%s","status":"ERROR"}}
                """.formatted(status, message));
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void happyPathMapsTextUsageStopReasonAndRequestId() throws Exception {
        stubResponse(200, """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello from Atrium!"}]},
                 "finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":17,"candidatesTokenCount":6,"totalTokenCount":23},
                 "responseId":"resp_abc123"}
                """);

        LlmResult result = client.complete(request());

        assertThat(result.content()).isEqualTo("Hello from Atrium!");
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.tokensIn()).isEqualTo(17);
        assertThat(result.tokensOut()).isEqualTo(6);
        assertThat(result.stopReason()).isEqualTo("end");
        assertThat(result.providerRequestId()).isEqualTo("resp_abc123");
    }

    @Test
    void toolCallResponseMapsToLlmToolCalls() throws Exception {
        stubResponse(200, """
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":
                 {"id":"call_1","name":"get_weather","args":{"city":"Paris","unit":"celsius"}}}]},
                 "finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":30,"candidatesTokenCount":12}}
                """);

        LlmResult result = client.complete(request());

        assertThat(result.content()).isNull();
        assertThat(result.stopReason()).isEqualTo("tool_use");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(result.toolCalls().get(0).name()).isEqualTo("get_weather");
        assertThat(result.toolCalls().get(0).arguments().get("city").asText()).isEqualTo("Paris");
    }

    // ── 13 §1.3 taxonomy, row by row ────────────────────────────────────────

    @Test
    void rateLimit429MapsToRateLimited() {
        stubError(429, "Resource exhausted");
        assertKind(LlmException.Kind.RATE_LIMITED);
    }

    @Test
    void serverErrorsMapToProviderDown() {
        stubError(500, "Internal error");
        assertKind(LlmException.Kind.PROVIDER_DOWN);

        wiremock.resetAll();
        stubError(503, "Model overloaded");
        assertKind(LlmException.Kind.PROVIDER_DOWN);
    }

    @Test
    void connectionFailureMapsToProviderDown() {
        // point at a port with nothing listening
        client = new GoogleClient(properties("goog-test-key", "http://127.0.0.1:1"), json);
        assertKind(LlmException.Kind.PROVIDER_DOWN);
    }

    @Test
    void contextOverflow400MapsToContextTooLong() {
        stubError(400, "The input token count exceeds the maximum context length");
        assertKind(LlmException.Kind.CONTEXT_TOO_LONG);
    }

    @Test
    void safetyFinishReasonMapsToContentFiltered() {
        stubResponse(200, """
                {"candidates":[{"content":{"role":"model","parts":[]},"finishReason":"SAFETY"}],
                 "usageMetadata":{"promptTokenCount":20,"candidatesTokenCount":0}}
                """);
        assertKind(LlmException.Kind.CONTENT_FILTERED);
    }

    @Test
    void blockedPromptWithNoCandidatesMapsToContentFiltered() {
        stubResponse(200, """
                {"candidates":[],"promptFeedback":{"blockReason":"SAFETY"}}
                """);
        assertKind(LlmException.Kind.CONTENT_FILTERED);
    }

    @Test
    void badKey401MapsToAuth() {
        stubError(401, "API key not valid");
        assertKind(LlmException.Kind.AUTH);
    }

    @Test
    void forbidden403MapsToAuth() {
        stubError(403, "Permission denied");
        assertKind(LlmException.Kind.AUTH);
    }

    @Test
    void missingKeyMapsToAuthWithoutAnyHttpCall() {
        client = new GoogleClient(properties("", wiremock.baseUrl()), json);
        assertKind(LlmException.Kind.AUTH);
        assertThat(wiremock.getAllServeEvents()).isEmpty();
    }

    @Test
    void unknownModel404MapsToUnknownModel() {
        stubError(404, "models/gemini-nonexistent is not found");
        assertKind(LlmException.Kind.UNKNOWN_MODEL);
    }

    @Test
    void otherBadRequestMapsToInvalidRequest() {
        stubError(400, "Invalid JSON payload received");
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
        stubError(401, "API key not valid");
        LlmException e = catchThrowableOfType(LlmException.class, () -> client.complete(request()));
        assertThat(e.getMessage()).doesNotContain("goog-test-key");
    }
}
