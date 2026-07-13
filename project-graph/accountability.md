# accountability

**What:** [[core-api]] module owning budgets ("payroll"), approvals, the append-only `task_events` audit log, usage metering, and analytics rollups.

**State: GUARD INTERFACE ONLY (M0.4, 2026-07-13 session 6b).** `BudgetGuard` interface + `NoopBudgetGuard` (always returns true) exist and are already wired into [[routing]]'s claim tx (`PostgresWorkBroker.claim`/`claimNext`) — but nothing enforces a real cap yet. Everything else — real budgets CRUD, `alert_pct` soft alert, hard-cap auto-pause, `StatsRollup` — is still M0.7/M2.3, per `atrium-docs/17-backend-execution-plan.md`. **Correction:** `UsageRecorder` does NOT live here — it's in [[execution]] (`app.atrium.execution.UsageRecorder`, built M0.5b), since it's called directly from `LlmLoopRuntime` in the same tx as `TaskService.complete()`. **Correction 2 (M0.6, 2026-07-13 session 6e):** the approval gate also does NOT live here — `TaskService.approve()`/`reject()` are in [[routing]] (same module as the state machine they guard), not a separate accountability service. This doc's "Key components" below described the target shape before those milestones landed; update per-component as each is actually built.

**Rev C additions ([[agent-platform]]):** budget tiers per Paperclip — `budgets.alert_pct` soft alert (`budget.threshold` event, once per period) + hard cap now **auto-pauses** the agent (`agents.paused`) besides refusing claims; usage idempotency namespaces widen to `taskId:attempt` / `learn:` / `embed:` (learning + embeddings are metered payroll too); StatsRollup becomes a durable outbox consumer at M2.3.

**Key components:**
- `BudgetGuard.canSpend(companyId, agentId)` — interface + Noop impl live HERE (accountability); consulted inside [[routing]]'s claim tx. Real cap check + over-cap → claim refused + `task_events(flagged, reason=budget_exceeded)` is M0.7.
- `UsageRecorder.record()` — lives in [[execution]], not here (M0.5b design call — it's called directly from `LlmLoopRuntime`, same tx as `TaskService.complete()`). M0.7 will make it also increment `budgets.spent_tokens` atomically in that same tx.
- `StatsRollup` — updates `agent_stats_daily` on approve/reject + nightly. Success rate = approved ÷ (approved+rejected). Not started — M2.3.

**Hard rules:** `task_events` is append-only — never UPDATE/DELETE. `pending_review` is a hard gate; nothing ships unreviewed.

**Contracts:** [[data-model]] (budgets, usage_records, task_events, agent_stats_daily) · `04-api-contract.md §Accountability` · `05-module-specs.md §accountability`.

Links: [[_Atrium]] · [[core-api]] · [[routing]] · [[execution]] · [[data-model]]
