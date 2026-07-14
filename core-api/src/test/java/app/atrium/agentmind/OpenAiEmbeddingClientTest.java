package app.atrium.agentmind;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Recorded-fixture tests (13 §2), mirroring AnthropicClientTest/GoogleClientTest. */
class OpenAiEmbeddingClientTest {

    static WireMockServer wiremock;
    OpenAiEmbeddingClient client;

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
        client = new OpenAiEmbeddingClient(properties("sk-test-key", wiremock.baseUrl()),
                new ObjectMapper());
    }

    private static EmbeddingProperties properties(String apiKey, String baseUrl) {
        return new EmbeddingProperties("openai", "text-embedding-3-small", Duration.ofSeconds(5),
                new EmbeddingProperties.OpenAi(baseUrl, apiKey));
    }

    @Test
    void notReadyWithoutAnApiKey() {
        OpenAiEmbeddingClient unconfigured = new OpenAiEmbeddingClient(properties(null, wiremock.baseUrl()),
                new ObjectMapper());
        assertThat(unconfigured.isReady()).isFalse();
        assertThatThrownBy(() -> unconfigured.embed("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void readyWithAnApiKey() {
        assertThat(client.isReady()).isTrue();
    }

    @Test
    void singleEmbedSendsAuthHeaderAndModelAndParsesTheVector() {
        wiremock.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}],
                         "model":"text-embedding-3-small","usage":{"prompt_tokens":3,"total_tokens":3}}
                        """)));

        float[] vector = client.embed("hello world");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
        wiremock.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
                .withHeader("Authorization", equalTo("Bearer sk-test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("text-embedding-3-small"))));
    }

    @Test
    void batchEmbedReturnsOneVectorPerInputInOrder() {
        wiremock.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"object":"list","data":[
                          {"object":"embedding","index":0,"embedding":[1.0,0.0]},
                          {"object":"embedding","index":1,"embedding":[0.0,1.0]}],
                         "model":"text-embedding-3-small","usage":{"prompt_tokens":6,"total_tokens":6}}
                        """)));

        List<float[]> vectors = client.embed(List.of("a", "b"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 0.0f);
        assertThat(vectors.get(1)).containsExactly(0.0f, 1.0f);
    }

    @Test
    void httpErrorMapsToAnIllegalStateExceptionWithTheProviderMessage() {
        wiremock.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(
                aResponse().withStatus(401).withHeader("Content-Type", "application/json").withBody("""
                        {"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}
                        """)));

        assertThatThrownBy(() -> client.embed("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Incorrect API key provided");
    }

    @Test
    void connectionFailureMapsToAnIllegalStateException() {
        // Nothing listens on this port — a plain connection-refused, no shared server touched.
        OpenAiEmbeddingClient unreachable = new OpenAiEmbeddingClient(
                properties("sk-test-key", "http://localhost:1"), new ObjectMapper());

        assertThatThrownBy(() -> unreachable.embed("hello")).isInstanceOf(IllegalStateException.class);
    }
}
