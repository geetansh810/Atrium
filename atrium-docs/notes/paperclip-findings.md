# Paperclip Findings (M0.0 — Paperclip half)

Researched 2026-07-12 from `github.com/paperclipai/paperclip` (shallow clone; docs read: `doc/SPEC.md`, `doc/execution-semantics.md`, `doc/memory-landscape.md`, `doc/DATABASE.md`, `skills/paperclip/SKILL.md`, `packages/db/src/schema/company_skills.ts`). License: MIT (verified LICENSE present). These are patterns to MINE, not code to fork (per 06 §B).

## What Paperclip actually is (confirmed)

Node.js/TypeScript + Express + React + Postgres (Drizzle ORM) control plane for teams of AI agents. Single-tenant self-hosted by design ("not a SaaS") — which confirms Atrium's differentiation (hosted, multi-tenant, verified, visual). Company-scoped everything; Board = human governance layer.

## Patterns adopted into Atrium specs (where → which doc)

1. **Adapter contract for agent runtimes** — every Paperclip agent adapter implements exactly `invoke(config, context?) / status(config) / cancel(config)`. Minimum contract to be an agent: *be callable*. Progressive integration levels: (1) callable, (2) status-reporting, (3) fully instrumented (cost + task updates via the control-plane REST API). → Atrium `AgentRuntime` SPI in `13-llm-and-agent-spi.md`.
2. **Context delivery modes** — "fat payload" (bundle context into the invocation) vs "thin ping" (wake signal; agent pulls what it needs via API). → runtime_config option in 13.
3. **Skills as versioned company data** — `company_skills` table: company_id, key, slug, name, description, **markdown body**, source_type/locator/ref, **trust_level**, file_inventory JSONB, categories[], sharing_scope, fork lineage (forked_from_skill_id), star/install counts, current_version_id → `company_skill_versions` (revision_number, file inventory with content). Skills are *taught to* agents (loaded into their context), not code. → Atrium skills registry in `14-skills-memory-learning.md`.
4. **Two-layer memory model** (their `memory-landscape.md`, surveying mem0/MemOS/supermemory/memsearch/etc.): control plane owns *binding, scoping, provenance, cost metering, browse/inspect UI, governance of destructive ops*; providers own *extraction, embedding, ranking, forgetting*. Portable core contract = ingest / recall / browse / get-by-handle / forget / usage-report; richer features behind capability flags. Common primitives across the whole landscape: ingest, query, scope, provenance, maintenance, context assembly. → Atrium `MemoryStore` SPI in 14.
5. **Budget three tiers** — visibility dashboards → soft alerts (warn at %) → hard ceiling with auto-pause + board notify + override. Costs denominated in tokens AND dollars; tracked per agent/task/project/company. **Billing codes**: when agent A delegates to B, B's spend attributes upstream to A's request. Request **depth** tracked as integer. → soft-alert threshold + billing attribution added in 15/16; Atrium already had hard ceiling at claim time.
6. **Checkout semantics** — single assignee invariant; atomic checkout; `409 = real owner exists — never retry`; separate `checkoutRunId` (ownership lock) from `executionRunId` (live run); stale-lock recovery is *crash recovery, not a retry loop*. → Confirms our claim/lease design; the never-retry-409 rule and the lock-vs-run distinction are written into 17's claim-loop card.
7. **Exact-once plan decomposition** — when an accepted plan spawns child tasks, the fingerprint `(sourceIssueId, acceptedPlanRevisionId)` is a durable uniqueness claim so re-wakes never create a second child tree. → Atrium M2.2 orchestrator card (17) uses `(parent_task_id, plan_artifact_id)` unique index.
8. **Structure ≠ dependency** — `parentId` is structural (rollup/why-exists); `blockedByIssueIds` is execution dependency (wake when resolved). Don't overload parent/child as dependency. → noted in 12 §task-graph; Atrium v1 keeps parent/child but the approval gate ("no open children") stays, and a future `task_blockers` table is sketched in 15.
9. **Pre-dispatch configuration validation** — missing secret/env bindings surface as a "configuration-incomplete" blocker BEFORE dispatching a run, never as a dispatched-then-failed run. → execution runner card in 17 (validate provider key present before claim loop starts; agent goes `flagged: config_incomplete`).
10. **Surface problems, don't silently fix** — no auto-reassign of stale work without visibility; recovery is explicit. Atrium's lease-reclaim IS automatic requeue (we differ deliberately — our agents are platform-run, not external), but every reclaim writes a `task_events(requeued)` row so it is never silent.
11. **Wake-reason taxonomy** — heartbeats carry `WAKE_REASON` (task assigned, comment, approval resolved, blockers resolved) + scoped wake payloads that skip inbox scanning. → event taxonomy in 12; our AgentRunner poll loop can evolve into event-driven wakes without schema change.

## Deliberate divergences

- **Java/Spring modular monolith**, not Node — owner strength; orchestration engine is not the differentiator (06 §B reasoning stands).
- **Platform-run agents by default** (our `llm_loop` runtime) vs Paperclip's external-agent-first stance. We adopt their adapter SPI so external agents become possible later without schema change.
- **Multi-tenant SaaS** with RLS vs single-tenant self-host.
- **Automatic lease reclaim** (visible via events) vs manual crash recovery.
- **Tasks + chat channels both exist** in Atrium (product surface requires chat); Paperclip is tasks-only-as-communication. We keep business coordination on tasks; chat is presentation.

## Reusable utilities checked

- No cleanly importable standalone price-table package found; model pricing lives in our own `model_catalog` table (13/15) seeded by hand from provider price pages.
