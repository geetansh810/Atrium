/**
 * Skills registry, MemoryStore SPI + PgVectorMemoryStore, EmbeddingClient,
 * ContextAssembler, governed LearningPipeline, knowledge ingestion.
 *
 * <p>Boundary (12 §2): depends on registry only. Never writes task state.
 * ContextAssembler reads only {@code status='active'} memories and
 * {@code trust_level IN ('platform','company')} skills (15 §5 invariant 7).
 * Full spec: atrium-docs/14.
 */
package app.atrium.agentmind;
