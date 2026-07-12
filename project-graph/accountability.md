# accountability

**What:** [[core-api]] module owning budgets ("payroll"), approvals, the append-only `task_events` audit log, usage metering, and analytics rollups.

**State: NOT STARTED.** Milestones M0.6 (approval gate), M0.7 (budget enforcement — amended card in `atrium-docs/17-backend-execution-plan.md`), M2.3 (rollups + analytics endpoints).

**Rev C additions ([[agent-platform]]):** budget tiers per Paperclip — `budgets.alert_pct` soft alert (`budget.threshold` event, once per period) + hard cap now **auto-pauses** the agent (`agents.paused`) besides refusing claims; usage idempotency namespaces widen to `taskId:attempt` / `learn:` / `embed:` (learning + embeddings are metered payroll too); StatsRollup becomes a durable outbox consumer at M2.3.

**Key components:**
- `BudgetGuard.canSpend(companyId, agentId)` — consulted inside [[routing]]'s claim tx; over-cap → claim refused + `task_events(flagged, reason=budget_exceeded)`. Never silent.
- `UsageRecorder.record()` — inserts `usage_records` with idempotency key + increments `budgets.spent_tokens` atomically, same tx as the [[execution]] LLM call bookkeeping.
- `StatsRollup` — updates `agent_stats_daily` on approve/reject + nightly. Success rate = approved ÷ (approved+rejected).

**Hard rules:** `task_events` is append-only — never UPDATE/DELETE. `pending_review` is a hard gate; nothing ships unreviewed.

**Contracts:** [[data-model]] (budgets, usage_records, task_events, agent_stats_daily) · `04-api-contract.md §Accountability` · `05-module-specs.md §accountability`.

Links: [[_Atrium]] · [[core-api]] · [[routing]] · [[execution]] · [[data-model]]
