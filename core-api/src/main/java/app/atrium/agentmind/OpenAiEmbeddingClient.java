package app.atrium.agentmind;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI's {@code /v1/embeddings} REST endpoint (13 §2, 06 §C allows REST over
 * an SDK). Same shape as {@link app.atrium.execution.GoogleClient}: a missing
 * key doesn't fail startup — {@link #isReady()} false means callers must
 * degrade gracefully rather than call {@link #embed}.
 *
 * <p>Uses {@link JdkClientHttpRequestFactory} rather than {@code
 * SimpleClientHttpRequestFactory} (GoogleClient's choice) — verified by hand
 * that the latter silently drops the HTTP error response body in this Spring
 * version ({@code RestClientResponseException.getResponseBodyAsByteArray()}
 * comes back empty), which would make every mapped error message generic
 * ("401 Unauthorized: [no body]") instead of the provider's real detail.
 */
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final ObjectMapper objectMapper;
    private final String model;

    @Nullable
    private final RestClient sdk;

    public OpenAiEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.model = properties.model();
        String apiKey = properties.openai().apiKey();
        this.sdk = (apiKey == null || apiKey.isBlank()) ? null
                : RestClient.builder()
                        .baseUrl(properties.openai().baseUrl())
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .requestFactory(clientHttpRequestFactory(properties.timeout()))
                        .build();
    }

    private static ClientHttpRequestFactory clientHttpRequestFactory(Duration timeout) {
        // HTTP/1.1 pinned: the JDK client's default h2c upgrade attempt is flaky
        // against WireMock's Jetty server in tests (RST_STREAM/EOF) and OpenAI's
        // real endpoint doesn't need h2 negotiation to be fast.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public boolean isReady() {
        return sdk != null;
    }

    @Override
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (sdk == null) {
            throw new IllegalStateException(
                    "Embeddings are not configured (OPENAI_API_KEY missing) — set it to use memory recall/seeding");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);

        JsonNode response;
        try {
            response = sdk.post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("OpenAI embeddings call failed ("
                    + e.getStatusCode() + "): " + errorMessage(e), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not reach OpenAI embeddings endpoint: " + e.getMessage(), e);
        }

        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            JsonNode values = item.path("embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            vectors.add(vector);
        }
        return vectors;
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
}
