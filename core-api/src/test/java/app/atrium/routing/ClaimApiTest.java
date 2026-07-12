package app.atrium.routing;

import static org.assertj.core.api.Assertions.assertThat;

import app.atrium.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M0.4 Done-when: 3 workers × 20 tasks → each claimed exactly once; an expired
 * lease requeues within 90s with a visible event; attempt increments once per
 * claim.
 */
class ClaimApiTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    // ── helpers ────────────────────────────────────────────────────────────

    private HttpHeaders headers(String companyId, String agentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (companyId != null) headers.set("X-Company-Id", companyId);
        if (agentId != null) headers.set("X-Agent-Id", agentId);
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
                new HttpEntity<>(Map.of("name", "Co " + slug, "slug", slug),
                        headers(null, null)), String.class);
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
                        "modelName", "claude-sonnet-5"), headers(companyId, null)),
                String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("id").asText();
    }

    private String createTask(String companyId, String title, String skill) {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/companies/" + companyId + "/tasks",
                new HttpEntity<>(Map.of("title", title, "requiredSkill", skill),
                        headers(companyId, null)), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return parse(response.getBody()).get("task").get("id").asText();
    }

    private ResponseEntity<String> claim(String companyId, String taskId, String agentId) {
        return rest.postForEntity("/api/v1/tasks/" + taskId + "/claim",
                new HttpEntity<>(headers(companyId, agentId)), String.class);
    }

    private ResponseEntity<String> renew(String companyId, String taskId, String agentId) {
        return rest.postForEntity("/api/v1/tasks/" + taskId + "/lease/renew",
                new HttpEntity<>(headers(companyId, agentId)), String.class);
    }

    private JsonNode getTask(String companyId, String taskId) {
        return parse(rest.exchange("/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers(companyId, null)), String.class).getBody());
    }

    private void expireLease(String taskId) {
        jdbc.update("UPDATE tasks SET lease_expires_at = now() - interval '1 minute' "
                + "WHERE id = ?::uuid", taskId);
    }

    /** Poll (≤ maxSeconds) until the task reaches the wanted status — scheduler-driven. */
    private void awaitStatus(String companyId, String taskId, String wanted, int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            last = getTask(companyId, taskId).get("task").get("status").asText();
            if (wanted.equals(last)) return;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
        throw new AssertionError("Task " + taskId + " never became '" + wanted
                + "' within " + maxSeconds + "s (last: '" + last + "')");
    }

    // ── Done-when: 3 workers × 20 tasks → each claimed exactly once ─────────

    @Test
    void threeWorkersTwentyTasks_eachClaimedExactlyOnce() throws Exception {
        String company = createCompany("race");
        List<String> agents = List.of(
                hireAgent(company, "Worker1", "coding"),
                hireAgent(company, "Worker2", "coding"),
                hireAgent(company, "Worker3", "coding"));
        List<String> taskIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            taskIds.add(createTask(company, "Race task " + i, "coding"));
        }

        ConcurrentLinkedQueue<String> wins = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> unexpected = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        for (String agentId : agents) {
            pool.submit(() -> {
                start.await();
                for (String taskId : taskIds) {
                    int code = claim(company, taskId, agentId).getStatusCode().value();
                    if (code == 200) {
                        wins.add(taskId);
                    } else if (code != 409) {
                        unexpected.add(code);       // 409 = lost the race, move on (16 §5)
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected).as("only 200 or 409 are legal claim outcomes").isEmpty();
        assertThat(wins).as("every task claimed, none twice")
                .hasSize(20).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(taskIds);

        for (String taskId : taskIds) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, attempt, assigned_agent_id FROM tasks WHERE id = ?::uuid",
                    taskId);
            assertThat(row.get("status")).isEqualTo("claimed");
            assertThat(row.get("attempt")).isEqualTo(1);
            assertThat(row.get("assigned_agent_id")).isNotNull();
            Integer claimedEvents = jdbc.queryForObject(
                    "SELECT count(*) FROM task_events WHERE task_id = ?::uuid "
                            + "AND event_type = 'claimed'", Integer.class, taskId);
            assertThat(claimedEvents).as("exactly one claimed event per task").isEqualTo(1);
        }
    }

    // ── Done-when: expired lease requeues (real scheduler) + attempt per claim ──

    @Test
    void expiredLeaseRequeuesWithVisibleEvent_andAttemptIncrementsPerClaim() {
        String company = createCompany("lease");
        String agent = hireAgent(company, "Sleepy", "coding");
        String taskId = createTask(company, "Long haul", "coding");

        assertThat(claim(company, taskId, agent).getStatusCode().value()).isEqualTo(200);
        assertThat(getTask(company, taskId).get("task").get("attempt").asInt()).isEqualTo(1);

        // Kill the lease; the 1s-interval scheduled sweep (60s in prod, ≤90s Done-when)
        // must requeue it — observed via the API, no manual job poke.
        expireLease(taskId);
        awaitStatus(company, taskId, "queued", 30);

        JsonNode task = getTask(company, taskId).get("task");
        assertThat(task.get("assignedAgentId").isNull()).isTrue();
        assertThat(task.get("leaseExpiresAt").isNull()).isTrue();

        // never silent: requeued audit row + pending outbox row
        List<Map<String, Object>> requeues = jdbc.queryForList(
                "SELECT actor, payload FROM task_events WHERE task_id = ?::uuid "
                        + "AND event_type = 'requeued'", taskId);
        assertThat(requeues).hasSize(1);
        assertThat(requeues.get(0).get("actor")).isEqualTo("system");
        assertThat(parse(requeues.get(0).get("payload").toString())
                .get("previousAgentId").asText()).isEqualTo(agent);
        Integer outbox = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'task.requeued' "
                        + "AND payload->>'taskId' = ?", Integer.class, taskId);
        assertThat(outbox).isEqualTo(1);

        // second claim: attempt increments exactly once per claim
        assertThat(claim(company, taskId, agent).getStatusCode().value()).isEqualTo(200);
        assertThat(getTask(company, taskId).get("task").get("attempt").asInt()).isEqualTo(2);
        Integer claimedEvents = jdbc.queryForObject(
                "SELECT count(*) FROM task_events WHERE task_id = ?::uuid "
                        + "AND event_type = 'claimed'", Integer.class, taskId);
        assertThat(claimedEvents).isEqualTo(2);
    }

    // ── claim 409 semantics: body says who holds it ──────────────────────────

    @Test
    void secondClaimIs409NamingTheHolder() {
        String company = createCompany("holder");
        String first = hireAgent(company, "First", "coding");
        String second = hireAgent(company, "Second", "coding");
        String taskId = createTask(company, "Contested", "coding");

        assertThat(claim(company, taskId, first).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> lost = claim(company, taskId, second);
        assertThat(lost.getStatusCode().value()).isEqualTo(409);
        assertThat(lost.getBody()).as("body says who holds it").contains("agent:" + first);
        assertThat(lost.getBody()).contains("do not retry");
    }

    // ── paused agents and wrong-skill agents cannot claim ────────────────────

    @Test
    void pausedOrUnskilledAgentsCannotClaim() {
        String company = createCompany("guards");
        String coder = hireAgent(company, "Coder", "coding");
        String scribe = hireAgent(company, "Scribe", "writing");
        String taskId = createTask(company, "Code it", "coding");

        // wrong skill → 409
        assertThat(claim(company, taskId, scribe).getStatusCode().value()).isEqualTo(409);

        // paused → 409 (16 §1: paused refuses claims)
        ResponseEntity<String> pause = rest.exchange("/api/v1/agents/" + coder,
                HttpMethod.PATCH, new HttpEntity<>(Map.of("paused", true),
                        headers(company, null)), String.class);
        assertThat(pause.getStatusCode().value()).isEqualTo(200);
        assertThat(claim(company, taskId, coder).getStatusCode().value()).isEqualTo(409);

        // unpause → claim succeeds
        rest.exchange("/api/v1/agents/" + coder, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("paused", false), headers(company, null)), String.class);
        assertThat(claim(company, taskId, coder).getStatusCode().value()).isEqualTo(200);
    }

    // ── lease renewal ─────────────────────────────────────────────────────────

    @Test
    void leaseRenewalExtendsOnlyForTheHolder() {
        String company = createCompany("renew");
        String holder = hireAgent(company, "Holder", "coding");
        String intruder = hireAgent(company, "Intruder", "coding");
        String taskId = createTask(company, "Heartbeat", "coding");

        // no lease yet → 409
        assertThat(renew(company, taskId, holder).getStatusCode().value()).isEqualTo(409);

        assertThat(claim(company, taskId, holder).getStatusCode().value()).isEqualTo(200);
        Instant before = Instant.parse(getTask(company, taskId)
                .get("task").get("leaseExpiresAt").asText());

        // push the lease low so the renewal visibly extends it
        jdbc.update("UPDATE tasks SET lease_expires_at = now() + interval '1 minute' "
                + "WHERE id = ?::uuid", taskId);
        ResponseEntity<String> renewed = renew(company, taskId, holder);
        assertThat(renewed.getStatusCode().value()).isEqualTo(200);
        Instant after = Instant.parse(parse(renewed.getBody()).get("leaseExpiresAt").asText());
        assertThat(after).isAfter(before.minusSeconds(120));    // back to ~10 min out
        assertThat(after).isAfter(Instant.now().plusSeconds(8 * 60));

        // someone else's heartbeat → 409; renewals never write audit rows
        assertThat(renew(company, taskId, intruder).getStatusCode().value()).isEqualTo(409);
        Integer events = jdbc.queryForObject(
                "SELECT count(*) FROM task_events WHERE task_id = ?::uuid", Integer.class, taskId);
        assertThat(events).as("created + claimed only — renew is not a state change").isEqualTo(2);
    }

    // ── tenant + header guards ────────────────────────────────────────────────

    @Test
    void crossTenantClaimIs404AndMissingAgentHeaderIs400() {
        String companyA = createCompany("claim-iso-a");
        String companyB = createCompany("claim-iso-b");
        hireAgent(companyA, "Honest", "coding");
        String agentB = hireAgent(companyB, "Foreign", "coding");
        String taskA = createTask(companyA, "Private", "coding");

        // B's agent + B's tenant header cannot see A's task at all
        assertThat(claim(companyB, taskA, agentB).getStatusCode().value()).isEqualTo(404);
        // B's agent smuggled under A's tenant header is not A's agent → 404
        assertThat(claim(companyA, taskA, agentB).getStatusCode().value()).isEqualTo(404);
        // missing X-Agent-Id → 400 problem+json
        ResponseEntity<String> missing = rest.postForEntity("/api/v1/tasks/" + taskA + "/claim",
                new HttpEntity<>(headers(companyA, null)), String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(400);
        assertThat(missing.getBody()).contains("X-Agent-Id");
    }
}
