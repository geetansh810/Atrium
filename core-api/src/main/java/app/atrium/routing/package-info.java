/**
 * Tasks, subtasks, queue semantics, claim/lease/reclaim, task graph,
 * WorkBroker interface (Postgres impl in v1).
 *
 * <p>Boundary (12 §2, 05 §routing): depends on registry (AgentDirectory) and
 * accountability (BudgetGuard). Every queue read is company-scoped; every
 * status change appends a task_event in the same transaction. Must not call
 * LLMs, know provider names, or contain role-specific branching —
 * {@code if (skill == …)} here is a bug by definition.
 */
package app.atrium.routing;
