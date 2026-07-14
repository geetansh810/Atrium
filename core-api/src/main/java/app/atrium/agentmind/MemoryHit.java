package app.atrium.agentmind;

/**
 * One recalled memory + its 14 §2 composite score (0.75·similarity +
 * 0.15·recency_decay + 0.10·use_count-bonus). Real shape as of M-MEM1 — was an
 * empty placeholder record until then (14 §6 note).
 */
public record MemoryHit(MemoryView memory, double score) {}
