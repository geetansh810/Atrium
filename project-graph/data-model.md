# data-model

**What:** the Postgres schema — the contract every layer mirrors ([[frontend-shared]] types, [[core-api]] entities). Flyway-managed, `V<N>__desc.sql`, never edit applied migrations. Full SQL: `atrium-docs/03-data-model.md` — **load that doc for any schema work; don't trust this summary for column details.**

**State: APPLIED through V6** (2026-07-15, M2.3 session — this line was stale at "V3" for several sessions; corrected here against the real `core-api/src/main/resources/db/migration/` listing, worth a periodic sanity-check going forward same as `07-milestones.md`'s tracker has needed). Real files: `V1__core.sql` (03 §V1 + 15 §1 ALTERs) · `V2__agent_platform.sql` (15 §§2–4, needs pgvector) · `V2_1__seed.sql` (M0.2, global role templates + model catalog) · `V3__budget_alerted_at.sql` (M0.7, `budgets.alerted_at`) · `V4__google_model_catalog.sql` (session 6i, Gemini rows) · `V5__seed_platform_skills.sql` (M-SK1) · `V6__agent_stats_daily.sql` (M2.3 — `agent_stats_daily`, with a `skill` column added to its PK beyond 03's original sketch, split out of the old planned "V3 communication_office" bundle since channels/messages/announcements/office_layout stay for M2.4c/M2.5). **Never edit an applied migration — gaps/renumbering only ever happens before M0.1 runs.** [[frontend-shared]] `types.ts` already mirrors everything through V6.

**Rev C:** `atrium-docs/15-data-model-delta.md` renumbers migrations by application order — V1 = core + ALTERs (agents.runtime_type/runtime_config/paused, tasks.attempt/billing_task_id/request_depth, budgets.alert_pct); V2 = agent platform (outbox_events, event_consumers, model_catalog, skills+attach, memories, knowledge, task_decompositions; needs pgvector); V3 = old V2 (comms/office); V4 = old V3 (RLS/billing). Claim query amended at 17 §M0.4.

**V1 tables (Phase 0):** companies · users · role_definitions (versioned, NULL company = global template) · agents (status enum: online/working/in_meeting/in_focus/away/offline) · tasks (status enum: queued/claimed/in_progress/flagged/pending_review/approved/rejected/cancelled; lease_expires_at) · subtasks · task_events (**append-only**) · budgets · usage_records (UNIQUE idempotency_key) · artifacts.
**V2 (Phase 2):** channels, messages, announcements, agent_stats_daily (live since V6), ~~office_layout~~ (dropped — office track retired 2026-07-15, never applied). **V3 (Phase 3):** RLS policies, billing_accounts.

**Invariants:** every business table has `company_id NOT NULL` + index · all IDs UUID · TIMESTAMPTZ · TEXT + CHECK instead of native enums · the canonical claim query (`FOR UPDATE SKIP LOCKED`) lives in 03 and is used by [[routing]] **verbatim**.

Links: [[_Atrium]] · [[core-api]] · [[registry]] · [[routing]] · [[accountability]] · [[frontend-shared]]
