package app.atrium.agentmind;

import java.util.List;

/**
 * Embeddings SPI (13 §2) — one bean selected by {@code atrium.embeddings.provider/model}.
 * Dimension is fixed by config (default {@code openai/text-embedding-3-small}, 1536-dim,
 * matching the {@code vector(1536)} columns in 15 §4.2/4.3); changing it is a new migration.
 *
 * <p>{@code isReady()} is this milestone's addition beyond 13 §2's sketch — mirrors
 * {@code execution.ProviderReadiness}'s pre-dispatch-gate role: {@link PgVectorMemoryStore#recall}
 * runs inside the claim transaction (same constraint as skills, M-CTX1), so a missing key must
 * degrade to "no memories" rather than fail the claim outright.
 */
public interface EmbeddingClient {

    boolean isReady();

    float[] embed(String text);

    List<float[]> embed(List<String> texts);
}
