package app.atrium.agentmind;

import java.util.UUID;

/** A near-duplicate found by {@link MemoryStore#findDuplicate} (14 §5 dedup). */
public record DuplicateMatch(UUID memoryId, double similarity) {}
