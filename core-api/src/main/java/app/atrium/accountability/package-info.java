/**
 * Budgets, BudgetGuard, UsageRecorder, approvals, task_events read API,
 * StatsRollup, Atrium Bot notices.
 *
 * <p>Boundary (12 §2, 05 §accountability): task_events are append-only;
 * {@code spent_tokens} only increases via usage_records inserts in the same
 * transaction; approval with open children/subtasks is refused.
 */
package app.atrium.accountability;
