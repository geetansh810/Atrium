package app.atrium.agentmind;

import java.util.UUID;

/**
 * One skill's slice of a {@link ContextBundle} (14 §6 step 1). {@code indexOnly}
 * means the full {@code bodyMd} didn't fit the budget and this is the
 * name+description fallback line instead — {@code bodyMd} is null in that case.
 */
public record SkillExcerpt(UUID skillId, String name, String description, String bodyMd, boolean indexOnly) {}
