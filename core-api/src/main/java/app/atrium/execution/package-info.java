/**
 * LlmProvider SPI + provider implementations, AgentRuntime SPI + LlmLoopRuntime,
 * PromptAssembler (pure function of role definition + task + feedback + bundle).
 *
 * <p>Boundary (12 §2): depends on routing (TaskService) and agentmind
 * (ContextAssembler). Secrets never meet prompts: PromptAssembler sees only
 * role definition, task fields, feedback, and the context bundle. Full SPI
 * contracts: atrium-docs/13.
 */
package app.atrium.execution;
