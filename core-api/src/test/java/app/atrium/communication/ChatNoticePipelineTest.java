package app.atrium.communication;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import app.atrium.execution.LlmLoopRuntime;
import app.atrium.execution.UsageRecorder;
import app.atrium.registry.runtime.AgentHandle;
import app.atrium.routing.TaskService;
import app.atrium.routing.WorkBroker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M2.5 Done-when: "an agent completing work produces a chat message." A real
 * {@code task.completed} (produced by the unmodified claim→complete pipeline)
 * is turned by the durable {@link ChatNoticePipeline} consumer into a bot
 * message in {@code #general}. {@code pollOnce} is called directly, same
 * precedent as {@link app.atrium.accountability.StatsRollupTest} — the scheduled
 * tick is dormant in the test profile.
 */
class ChatNoticePipelineTest extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired LlmLoopRuntime runtime;
    @Autowired WorkBroker workBroker;
    @Autowired TaskService taskService;
    @Autowired UsageRecorder usageRecorder;
    @Autowired ChatNoticePipeline chatNoticePipeline;
    @Autowired PlatformTransactionManager txManager;

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
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug), headers(null)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private String hireAgent(String companyId, String name, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/agents",
                new HttpEntity<>(Map.of(
                        "name", name,
                        "roleTemplateKey", "coder",
                        "roleTitle", "Engineer",
                        "skillTags", List.of(skill),
                        "modelProvider", "anthropic",
                        "modelName", "claude-sonnet-5"), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        String agentId = parse(response.getBody()).get("id").asText();
        runtime.stop(new AgentHandle(UUID.fromString(agentId), UUID.fromString(companyId)));
        return agentId;
    }

    private String createTask(String companyId, String title, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", title, "requiredSkill", skill), headers(companyId)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("task").get("id").asText();
    }

    /** Shared Testcontainers DB → this consumer must only see events it creates itself. */
    private void resetCursorToNow() {
        Long baseline = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox_events", Long.class);
        jdbc.update("""
                INSERT INTO event_consumers(consumer_name, last_event_id) VALUES (?, ?)
                ON CONFLICT (consumer_name) DO UPDATE SET last_event_id = EXCLUDED.last_event_id
                """, ChatNoticePipeline.CONSUMER_NAME, baseline);
    }

    private List<String> generalMessages(String companyId) {
        return jdbc.queryForList("""
                SELECT m.text FROM messages m JOIN channels c ON m.channel_id = c.id
                WHERE c.company_id = ?::uuid AND c.name = 'general' AND m.sender = 'bot'
                ORDER BY m.created_at
                """, String.class, companyId);
    }

    private void completeTask(UUID companyId, UUID taskId, UUID agentId) {
        workBroker.claim(companyId, taskId, agentId);
        taskService.progress(companyId, taskId, agentId, null, null, null);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            usageRecorder.record(companyId, agentId, taskId, 1, "anthropic", "claude-sonnet-5", 60, 40, 40L);
            taskService.complete(companyId, taskId, agentId, "text", "done");
        });
    }

    @Test
    void completingATaskPostsABotMessageToGeneral() {
        resetCursorToNow();
        String company = createCompany("m25-chat");
        String agentId = hireAgent(company, "Priya", "coding");
        String task = createTask(company, "Reverse a string", "coding");

        // created/claimed/progress events all flow through the pipeline first and must be ignored.
        completeTask(UUID.fromString(company), UUID.fromString(task), UUID.fromString(agentId));

        chatNoticePipeline.pollOnce(50);

        List<String> messages = generalMessages(company);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("Priya").contains("Reverse a string").contains("ready for review");

        // Cursor advanced — a second poll with nothing new adds no duplicate.
        chatNoticePipeline.pollOnce(50);
        assertThat(generalMessages(company)).hasSize(1);
    }

    @Test
    void ignoresNonCompletionEventsAndResolvesEachAgentName() {
        resetCursorToNow();
        String company = createCompany("m25-chat2");
        String coder = hireAgent(company, "Rohan", "coding");
        String writer = hireAgent(company, "Meera", "content");
        // Two tasks created (task.created events) — only the completed ones become messages.
        String t1 = createTask(company, "Build endpoint", "coding");
        String t2 = createTask(company, "Write blurb", "content");
        createTask(company, "Untouched task", "coding");   // never completed → no message

        completeTask(UUID.fromString(company), UUID.fromString(t1), UUID.fromString(coder));
        completeTask(UUID.fromString(company), UUID.fromString(t2), UUID.fromString(writer));

        chatNoticePipeline.pollOnce(50);

        List<String> messages = generalMessages(company);
        assertThat(messages).hasSize(2);
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Rohan").contains("Build endpoint"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("Meera").contains("Write blurb"));
    }
}
