package app.atrium.realtimebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * M0.75 Done-when: create a task → a Redis subscriber receives {@code
 * task.created} within 1s; killing the relay mid-batch loses nothing (rows
 * stay unpublished, a re-run publishes them). "Killed mid-batch" is
 * simulated by forcing the Redis publish call to throw — the relay's whole
 * claim+publish+mark transaction rolls back exactly as it would on a real
 * process crash between claiming and committing (see {@link OutboxRelay}).
 *
 * <p>{@code @MockitoSpyBean} gives this class its own Spring context (a
 * different bean set = a different context-cache key), so its relay ticks
 * fast (overriding the test-suite-wide slow default in
 * {@code src/test/resources/application-test.yml}) without racing every
 * other test class's shared, un-mocked relay instance for the same rows.
 */
@TestPropertySource(properties = "atrium.relay.interval-ms=250")
class OutboxRelayTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @MockitoSpyBean
    StringRedisTemplate redisTemplate;

    private HttpHeaders headers(String companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) headers.set("X-Company-Id", companyId);
        return headers;
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Unparseable response: " + body, e);
        }
    }

    private String createCompany(String slugPrefix) {
        String slug = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/companies",
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), headers(null)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private void hireAgent(String companyId, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", "Relay Test Agent",
                        "roleTemplateKey", "coder",
                        "roleTitle", "Engineer",
                        "skillTags", java.util.List.of(skill),
                        "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    private void createTask(String companyId, String title, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", title, "requiredSkill", skill), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    private Long insertRawOutboxRow(UUID companyId) {
        return jdbc.queryForObject("""
                INSERT INTO outbox_events(company_id, topic, event_type, payload)
                VALUES (?, 'test/topic', 'test.event', '{}'::jsonb)
                RETURNING id
                """, Long.class, companyId);
    }

    private boolean isPublished(Long id) {
        Boolean published = jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_events WHERE id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(published);
    }

    private void awaitPublished(Long id, int maxSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isPublished(id)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("row " + id + " was never published within " + maxSeconds + "s");
    }

    @Test
    void redisSubscriberReceivesTaskCreatedWithinOneSecond() throws Exception {
        String companyId = createCompany("relay");
        hireAgent(companyId, "coding");
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener((message, pattern) -> messages.add(new String(message.getBody())),
                new ChannelTopic("atrium:events:" + companyId));
        container.afterPropertiesSet();
        container.start();
        try {
            createTask(companyId, "reverse a string", "coding");

            String received = messages.poll(1, TimeUnit.SECONDS);
            assertThat(received).as("no message received on the company's channel within 1s").isNotNull();

            JsonNode event = parse(received);
            assertThat(event.get("type").asText()).isEqualTo("task.created");
            assertThat(event.get("taskId").asText()).isNotBlank();
            assertThat(event.has("ts")).isTrue();
        } finally {
            container.stop();
        }
    }

    @Test
    void killingRelayMidBatchLosesNothing() throws Exception {
        // Stub the fault BEFORE the rows exist, so no real tick can slip in
        // and publish them before the "crash" takes effect.
        doThrow(new RuntimeException("simulated crash mid-relay"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());

        UUID companyId = UUID.randomUUID();
        Long id1 = insertRawOutboxRow(companyId);
        Long id2 = insertRawOutboxRow(companyId);

        Thread.sleep(600);     // a couple of relay ticks (250ms interval), all failing
        assertThat(isPublished(id1)).isFalse();
        assertThat(isPublished(id2)).isFalse();

        // "Restart": the fault clears, the next tick re-claims the same
        // still-unpublished rows and this time succeeds.
        reset(redisTemplate);

        awaitPublished(id1, 3);
        awaitPublished(id2, 3);
    }
}
