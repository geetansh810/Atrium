package app.atrium.execution;

/**
 * Placeholder for the real bundle 14 §6's ContextAssembler will build at
 * M-CTX1 (skills/memories/knowledge under a token budget). Always {@code null}
 * in M0.5b — {@link PromptAssembler} treats a null bundle as "sections absent".
 */
public record ContextBundle() {}
