package app.atrium.agentmind;

import java.util.UUID;

/**
 * One retrieved knowledge chunk + its plain cosine score (14 §3 step 3 — no
 * recency/use-count blend the way {@link MemoryHit}'s score has one, since
 * {@code knowledge_chunks} carries no use-tracking columns). Real shape as of
 * M-KN1 — was an empty placeholder record until then (14 §6 note).
 */
public record KnowledgeHit(UUID chunkId, UUID docId, String docTitle, int seq, String content, double score) {}
