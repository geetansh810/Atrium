package app.atrium.agentmind;

import java.util.List;
import java.util.UUID;

/**
 * What {@link ContextAssembler} builds under a token budget (14 §6) — the
 * single doorway into {@code PromptAssembler}. {@code memories}/{@code
 * knowledge} stay empty until M-MEM1/M-KN1 build their assembler steps;
 * {@code provenanceIds} is every skill/memory/knowledge id actually included,
 * written into {@code task_events(claimed).payload.contextProvenance} so
 * every prompt is auditable.
 */
public record ContextBundle(List<SkillExcerpt> skills, List<MemoryHit> memories,
                             List<KnowledgeHit> knowledge, List<UUID> provenanceIds, int tokenCount) {}
