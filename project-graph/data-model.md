# data-model

**What:** the Postgres schema — the contract every layer mirrors ([[frontend-shared]] types, [[core-api]] entities). Flyway-managed, `V<N>__desc.sql`, never edit applied migrations. Full SQL: `atrium-docs/03-data-model.md` — **load that doc for any schema work; don't trust this summary for column details.**

**State: APPLIED through V2** (M0.1, 2026-07-12). `V1__core.sql` (03 §V1 + 15 §1 ALTERs folded) and `V2__agent_platform.sql` (15 §§2–4) live in `core-api/src/main/resources/db/migration/` and apply clean on pgvector/pgvector:pg16 (compose + Testcontainers). **From now on: never edit V1/V2 — they are applied.** [[frontend-shared]] `types.ts` already mirrors it.

**Rev C:** `atrium-docs/15-data-model-delta.md` renumbers migrations by application order — V1 = core + ALTERs (agents.runtime_type/runtime_config/paused, tasks.attempt/billing_task_id/request_depth, budgets.alert_pct); V2 = agent platform (outbox_events, event_consumers, model_catalog, skills+attach, memories, knowledge, task_decompositions; needs pgvector); V3 = old V2 (comms/office); V4 = old V3 (RLS/billing). Claim query amended at 17 §M0.4.

**V1 tables (Phase 0):** companies · users · role_definitions (versioned, NULL company = global template) · agents (status enum: online/working/in_meeting/in_focus/away/offline) · tasks (status enum: queued/claimed/in_progress/flagged/pending_review/approved/rejected/cancelled; lease_expires_at) · subtasks · task_events (**append-only**) · budgets · usage_records (UNIQUE idempotency_key) · artifacts.
**V2 (Phase 2):** channels, messages, announcements, agent_stats_daily, office_layout. **V3 (Phase 3):** RLS policies, billing_accounts.

**Invariants:** every business table has `company_id NOT NULL` + index · all IDs UUID · TIMESTAMPTZ · TEXT + CHECK instead of native enums · the canonical claim query (`FOR UPDATE SKIP LOCKED`) lives in 03 and is used by [[routing]] **verbatim**.

Links: [[_Atrium]] · [[core-api]] · [[registry]] · [[routing]] · [[accountability]] · [[frontend-shared]]
