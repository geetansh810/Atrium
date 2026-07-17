package app.atrium.agentmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import app.atrium.IntegrationTestBase;
import app.atrium.common.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 17 §M-MEM1 Done-when: recall latency &lt;100ms at 10k memories. Rows are
 * seeded directly via batch JDBC (random vectors, {@link EmbeddingClient}
 * mocked) rather than through 10k real ingest() calls — this test is about
 * the pgvector HNSW-indexed query, not about exercising the ingest path at scale.
 */
class MemoryRecallPerformanceTest extends IntegrationTestBase {

    private static final int ROW_COUNT = 10_000;
    private static final int DIMENSIONS = 1536;

    JdbcTemplate jdbc = adminJdbc();

    @Autowired
    MemoryStore memoryStore;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @BeforeEach
    void stubEmbeddings() {
        when(embeddingClient.isReady()).thenReturn(true);
        float[] queryVector = new float[DIMENSIONS];
        queryVector[0] = 1f;
        when(embeddingClient.embed(anyString())).thenReturn(queryVector);
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    @Test
    void recallStaysUnder100msAt10kMemories() {
        UUID companyId = UUID.randomUUID();
        jdbc.update("INSERT INTO companies (id, name, slug) VALUES (?, ?, ?)",
                companyId, "Perf Co", "perf-" + companyId.toString().substring(0, 8));

        // Bulk filler: random, uncorrelated vectors, generated in-database (a 10k-row
        // JDBC batch round-trip of 1536-float literals was the actual bottleneck here —
        // ~130s — so the fixture itself is a single SQL statement instead).
        jdbc.update("""
                INSERT INTO memories (id, company_id, scope, kind, content, embedding, status, provenance)
                SELECT gen_random_uuid(), ?, 'company', 'fact', 'generated fact #' || i,
                       (SELECT ('[' || string_agg(random()::text, ',') || ']')::vector
                        FROM generate_series(1, ?)),
                       'active', '{}'::jsonb
                FROM generate_series(1, ?) AS i
                """, companyId, DIMENSIONS, ROW_COUNT);

        // One genuinely relevant memory, matching the query vector exactly, so recall
        // has something real to surface above the 0.30 score threshold.
        float[] queryVector = new float[DIMENSIONS];
        queryVector[0] = 1f;
        jdbc.update("""
                INSERT INTO memories (id, company_id, scope, kind, content, embedding, status, provenance)
                VALUES (gen_random_uuid(), ?, 'company', 'preference', 'the relevant one',
                        CAST(? AS vector), 'active', '{}'::jsonb)
                """, companyId, vectorLiteral(queryVector));

        // M3.2: bare service calls (no wrapping HTTP request) — bind the
        // tenant for RLS the same way LlmLoopRuntime does (08 §Security rule
        // 6); ThreadLocal set/clear overhead is negligible against the 100ms budget.
        // warm-up call (JIT/connection/HNSW graph traversal) — not part of the timed assertion
        TenantContext.runAsSystem(companyId, () ->
                memoryStore.recall(new RecallQuery(companyId, UUID.randomUUID(), null, "warmup query", 12, null)));

        long start = System.nanoTime();
        List<MemoryHit> hits = TenantContext.callAsSystem(companyId, () -> memoryStore.recall(
                new RecallQuery(companyId, UUID.randomUUID(), null, "a real task query", 12, null)));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(hits).extracting(h -> h.memory().content()).contains("the relevant one");
        assertThat(elapsedMs).as("recall latency at %d memories", ROW_COUNT).isLessThan(100);
    }
}
