/**
 * Budgets, BudgetGuard, UsageRecorder, approvals, task_events read API,
 * StatsRollup, Atrium Bot notices.
 *
 * <p>Boundary (12 §2, 05 §accountability): task_events are append-only;
 * {@code spent_tokens} only increases via usage_records inserts in the same
 * transaction; approval with open children/subtasks is refused. Never
 * depends on routing — {@code StatsRollupWorker} (M2.3) reads only the
 * {@code agentId}/{@code requiredSkill}/{@code attempt} fields routing
 * already puts on {@code task.completed|approved|rejected} outbox payloads,
 * never a {@code Task} entity or {@code TaskRepository}.
 */
package app.atrium.accountability;
