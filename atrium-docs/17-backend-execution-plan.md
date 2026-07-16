# 17 — Backend Execution Plan (session-by-session)

The build script for the backend. Each card = one LLM session; paste per `09-llm-workflow.md` (priming block + this card + the listed contract sections). All research is done — cards reference only 03/04/05 + 12–16. If a session needs a decision not written here, that is a contract bug: stop, fix docs, continue (09 rule 2).

**Amendments to 07-milestones.md:** M0.1/M0.2/M0.4 cards below supersede their 07 versions; M0.5 splits into M0.5a/b; M0.8 is redefined (the task board already exists on mocks — the milestone becomes the mock→API swap); new M0.75 and the M-SK/CTX/MEM/LN/KN/AR series slot in as shown. Everything from M1.2 onward in 07 is unchanged.

## Recommended order

```
MB-0 → M0.1 → M0.2 → M0.3 → M0.4 → M0.5a → M0.5b → M0.6 → M0.7 → M0.75 → M0.8
     → M-SK1 → M-CTX1 → M-MEM1 → M-LN1 → M1.1 → M1.2 → M1.3 (pilot now benefits from learning)
     → M-KN1 → M-AR1 → M-LN2 → Phase 2 per 07 (M2.x unchanged; M2.4c consumes M0.75's relay)
```

Cross-cutting Definition of Done for every card: 08 §DoD + every query company-scoped + outbox row in-tx for every state change + `make check` green.

---

### MB-0 — Git init & monorepo shape · deps: none (chore session)
Layout per 02 §2: `git init` at repo root; move nothing (web/ already correct); add root `.gitignore` (node_modules, target, .env, dist), `.env.example` (08 §Config vars + `EMBEDDINGS_*`), README stub, `Makefile` (`dev`, `check`, `test` targets that no-op gracefully for services not yet present). Commit `chore: init monorepo (web frontend on mocks + docs)`.
✅ Done when: `git log` shows one commit containing web/, atrium-docs/, project-graph/; `make check` runs web lint+typecheck.

### M0.1 — Scaffold & data model (supersedes 07 card) · deps: MB-0
**Paste:** 02 §2, 03 all, 15 all, 05 §registry(skim), 12 §2.
**Build:** `core-api/` Maven, Java 17, Spring Boot 3.x (web, data-jpa, validation, actuator, flyway, postgres driver, spring-data-redis); package skeleton per 12 §2 (empty modules with `package-info.java` boundary docs); `common/`: `TenantContext` (from `X-Company-Id`/`X-User-Id` filter), problem+json `@ControllerAdvice`, UUID/Clock utils. Flyway `V1__core.sql` (03 §V1 verbatim + 15 §1 ALTERs folded) and `V2__agent_platform.sql` (15 §§2–4, incl. `CREATE EXTENSION IF NOT EXISTS vector`). `docker-compose.yml`: `pgvector/pgvector:pg16`, `redis:7`, core-api. Testcontainers base class (pgvector image!).
✅ Done when: `docker compose up` boots clean; both migrations apply; `GET /actuator/health`=200; a Testcontainers smoke test inserts+reads a company.

### M0.2 — Registry module (supersedes 07 card) · deps: M0.1
**Paste:** 03 (companies/users/role_definitions/agents), 04 §Registry, 16 §1, 05 §registry, 13 §3.1 (RuntimeRegistry part).
**Build:** entities+repos (every repo method takes companyId); endpoints per 04 §Registry + 16 §1 (`runtimeType/runtimeConfig/paused` on hire/patch; `GET /model-catalog`); `RuntimeRegistry` interface + descriptor for `llm_loop` (validateConfig only — no runtime yet); hire via `roleTemplateKey` resolves seeded template; manager-cycle validation (walk-up with visited set); `AgentDirectory.findBySkill` service interface; seed data migration `V2_1__seed.sql`: 3 global role templates (coder/tester/research: system_prompt, output_contract, allowed_tools) + model_catalog rows (15 §4.0 comment).
✅ Done when: 07's M0.2 check + hiring with bad `runtimeConfig` → 400 field errors + `/model-catalog` returns seeded rows.

### M0.3 — Tasks, routing & event backbone (writer) · deps: M0.2
**Paste:** 03 (tasks/subtasks/task_events), 04 §Tasks (create/list/get/events), 05 §routing, 12 §§3–4, 15 §2.
**Build:** `eventbus/`: `DomainEvent` record, `OutboxWriter.append(companyId, topic, type, payload)` (same-tx, called by services); `routing/`: create/list/get task endpoints (+subtasks, pagination envelope), skill validated via `AgentDirectory`, `TaskStateGuard` (legal transitions matrix; wrong status→409), `TaskEventRecorder` (append `task_events` + outbox in the SAME tx — one helper so it's impossible to forget); `billing_task_id`/`request_depth` set on create (root=self / parent-copy).
✅ Done when: 07's M0.3 isolation check (second company sees nothing — test committed) + creating a task writes exactly one `task_events` row AND one `outbox_events` row in one tx (asserted by test).

### M0.4 — Claim loop & lease (supersedes 07 card) · deps: M0.3
**Paste:** 03 §claim query + this card (query amendment), 04 (claim/renew), 05 §routing, 15 §1 semantics.
**Build:** canonical claim query with two amendments (this text supersedes 03's SQL — update 03 inline comment when merging): `attempt = attempt + 1` in the SET list, and the inner SELECT gains `AND NOT EXISTS (SELECT 1 FROM agents a WHERE a.id=$agentId AND a.paused)` plus a `BudgetGuard.canSpend` call in the same service tx (stub returning true until M0.7). `POST /tasks/{id}/claim` (X-Agent-Id), `POST /tasks/{id}/lease/renew`; `LeaseReclaimJob` @Scheduled 60s under `pg_try_advisory_lock` (12 §8.3), requeues + `task_events(requeued)` + outbox. Claim 409 → body says who holds it; **document in Worker API: never retry a 409** (16 §5).
✅ Done when: 07's 3×20 exactly-once concurrency test passes; expired lease requeues within 90s with visible event; `attempt` increments once per claim (test).

### M0.5a — LLM provider SPI + Anthropic · deps: M0.2
**Paste:** 13 §1 entire (normative), 08 §Security.
**Build:** `execution/spi/` records+interfaces verbatim from 13 §1.1; `LlmRouter` (catalog validation, timeout `atrium.llm.timeout=120s`, error mapping table 13 §1.3, retry policy in-attempt); `AnthropicClient implements LlmProvider` via official Java SDK (06 §C), key from `ANTHROPIC_API_KEY` only; cost computation from `model_catalog`. Tests: WireMock/recorded fixtures for happy path + each error kind mapping.
✅ Done when: integration test (real key, `tier=fast` model, skipped in CI without key) returns text + nonzero token counts; every 13 §1.3 row has a mapping test.

### M0.5b — Agent runtime `llm_loop` (with M0.5a completes 07's M0.5) · deps: M0.4, M0.5a
**Paste:** 13 §3 entire, 05 §execution, 12 §7/§9, 03 (usage_records/artifacts).
**Build:** `AgentRuntime` SPI verbatim; `RuntimeRegistry` full; `AgentLifecycleService` (start on hire/unpause, stop on pause/budget/shutdown); `LlmLoopRuntime` loop per 13 §3.2 on virtual threads (steps 0–7; ContextAssembler not built yet — pass `bundle=null`); `PromptAssembler.build(roleDef, task, feedback?, bundle?)` pure, layout per 14 §6 (skills/memories sections simply absent when bundle null); `UsageRecorder.record` with `taskId:attempt` unique key (same tx as progress write); completion → artifact + `pending_review` + events; failure → flag per taxonomy; pre-dispatch gate (missing key ⇒ flag `config_incomplete`, park loop).
✅ Done when: 07's M0.5 check verbatim (real artifact from "reverse a string"; redelivered task doesn't double-record — test forces a requeue mid-work).

### M0.6 — Approval gate & audit · deps: M0.5b — **07 card unchanged**, plus: approve/reject also outbox-publish `task.approved|rejected` (payload incl. `feedback`) because M-LN1 consumes them.
✅ 07's check + events visible in `outbox_events`.

### M0.7 — Budget enforcement (supersedes 07 card: adds soft tier + auto-pause) · deps: M0.5b
**Paste:** 03 (budgets/usage_records), 04 §Accountability, 05 §accountability, 15 §1 (alert_pct), 16 §4.
**Build:** budgets CRUD; real `BudgetGuard.canSpend` inside claim tx (checks agent cap AND company-wide cap rows); `UsageRecorder` also increments `budgets.spent_tokens` atomically same tx; crossing `alert_pct` → `budget.threshold` outbox event once per period (dedupe via idempotent check on prior event or a `alerted_at` column — pick the column, simpler); cap hit → claim refused + `task_events(flagged, budget_exceeded)` + `budget.exceeded` event + **auto-pause** (`agents.paused=true` via lifecycle service, Paperclip hard-ceiling behavior); unpause = PATCH `paused:false` after raising cap.
✅ Done when: 07's M0.7 check + threshold event fires exactly once + over-cap agent shows paused in roster and resumes on unpause.

### M0.75 — Outbox relay & realtimebridge (NEW) · deps: M0.3
**Paste:** 12 §§3–4, 15 §2, 05 §realtimebridge, 04 §WS events.
**Build:** `OutboxRelay` @Scheduled 250ms, batch 100, `SELECT … WHERE published_at IS NULL ORDER BY id FOR UPDATE SKIP LOCKED`, publish to Redis channel `atrium:events:{companyId}` (payload = `{type: event_type, …payload, ts}`), stamp `published_at`; advisory lock; retention job (15 §2); `event_consumers` cursor helper (`EventCursorWorker` base class: poll batch beyond cursor, handle, advance — used by M-LN1/M2.3); metrics: relay lag, pending count (10 §6).
✅ Done when: integration test — create task; a Redis test-subscriber receives `task.created` within 1s; killing relay mid-batch loses nothing (rows stay unpublished, re-run publishes).

### M0.8 — Dashboard on real API (REDEFINED: board exists, swap mocks) · deps: M0.2–M0.7, frontend sessions 1–3
**Paste:** 04 all, 16 §1, web `store.tsx`/`mockData.ts` (the swap point built in session 2), 08 conventions (React Query).
**Build (web/):** `src/shared/api.ts` typed client (dev headers from config); introduce React Query; reimplement store reads over API while keeping component props identical; task create/approve/reject/flag wired; seed script (`scripts/seed-dev.ts` or SQL) reproducing the 6-agent demo company so the office keeps working; mock layer stays behind `VITE_USE_MOCKS=1`.
✅ Done when: full loop in browser against real backend — create task → watch agent claim/work (real LLM) → pending_review → approve → budget bar moves. Office canvas still renders agents (locationKey mapping from live statuses).

### M-SK1 — Skills registry (NEW) · deps: M0.2
**Paste:** 14 §§0–1, 15 §4.1, 16 §§1–2.
**Build (agentmind/):** skills entities/repos/endpoints per 16 §2; attach tables + hire auto-attach (template→`agent_skills(source='hired')`); versioning (new version = new row; attach pins id); `GET /agents/{id}/mind` (skills part; memory counts zeroed); seed `V2_2__seed_platform_skills.sql`: 2–3 platform skills per seeded template (write real bodies — e.g. coder: "code review checklist", "output format: unified diff"; research: "source-ranking procedure", "summarization contract").
✅ Done when: hire from template → agent has template skills; attach/detach reflected in `/mind`; global+company listing correct across two seeded companies (isolation test).

### M-CTX1 — ContextAssembler v1: skills into prompts (NEW) · deps: M-SK1, M0.5b
**Paste:** 14 §6, 13 §3.2.
**Build:** `ContextAssembler` interface + impl (skills slot only; memories/knowledge lists empty); token estimation (chars/4 heuristic, documented); budget from `runtime_config.contextBudgetTokens` default 4000, cap 30% of catalog context window; deterministic ordering + item-granular truncation + index-line fallback; provenance ids into `task_events(claimed).payload.contextProvenance`; wire into `LlmLoopRuntime` step 2.
✅ Done when: unit tests — fits-all, truncation, fallback, determinism (same inputs ⇒ byte-identical bundle); integration: claimed task's event shows skill ids; prompt fixture contains "## Your skills".

### M-MEM1 — Memory store + recall (NEW) · deps: M-CTX1
**Paste:** 14 §2 + SPI, 13 §2, 15 §4.2, 16 §3 (browse/seed/forget rows).
**Build:** `EmbeddingClient` (OpenAI impl or Anthropic-compatible per config; usage metered `embed:` namespace); `PgVectorMemoryStore` (scoring formula 14 §2 verbatim; scope-set query; use_count/last_used_at update on recall); memories endpoints: browse (+semantic `q`), manual seed (active), forget(archive); ContextAssembler memories slot (≤35%, k=12, threshold 0.30); nightly TTL archiver.
✅ Done when: seed "CEO prefers bullet lists" as company-scope preference → next task's prompt fixture contains it under "## What you have learned here"; scope isolation test (agent-A memory never recalled for agent-B, company-B never for company-A); recall latency <100ms at 10k memories (generated fixture).

### M-LN1 — Learning pipeline + review governance (NEW) · deps: M-MEM1, M0.75, M0.6
**Paste:** 14 §5 entire (policy table is normative), 16 §§3–4, 12 §3 (durable consumers).
**Build:** `LearningPipeline extends EventCursorWorker` consuming `task.rejected|approved|completed`; extraction prompts (write them in-session, commit as resources — keep ≤3 lessons / ≤120-token summary contracts from 14 §5); `learn:{eventId}` idempotency; governance routing (auto-active vs pending_review per policy); dedupe (≥0.92 similarity → importance bump); review-queue + review action endpoints (approve/reject/promote/convert-to-draft-skill); `memory.*` outbox events; Atrium Bot notice on review_requested.
✅ Done when: reject a task with feedback "never use passive voice; our brand name is X-Corp" → agent gets an auto-active lesson AND a pending company `fact`; approving the fact makes it recall for OTHER agents; replaying the event (cursor reset) creates nothing new (idempotency test); rejected memory never appears in prompts.

### M-KN1 — Knowledge ingestion (NEW) · deps: M-MEM1
Per 14 §3 + 15 §4.3 + 16 §3: text/markdown ingestion → chunk(800/100) → embed → recall slot 3 (≤15%, top-3 ≥0.35); role attach; archive.
✅ Done when: upload brand guide; content-role agent's prompt cites it; unrelated role's doesn't (role attach respected).

### M-AR1 — Runtime extensibility proof (NEW) · deps: M0.5b, M0.75
**Paste:** 13 §§3.3–3.4, 16 §5.
**Build:** `EchoRuntime` (decided over `ProcessRuntime` — real subprocess sandboxing is a rabbit hole this card doesn't need; an internal virtual-thread poll loop, same shape as `LlmLoopRuntime`, that claims and completes with a canned artifact via `TaskService`, zero LLM calls, zero usage recording since there's no real spend).
**Build decision, docs-first (this amendment):** the fat-claim response (`ContextBundle` embedded in the Worker API's `POST /tasks/{id}/claim` body per 16 §5) is explicitly DEFERRED, not built this session. `EchoRuntime`, like `llm_loop`, is an internal poll-loop runtime — it calls `WorkBroker`/`TaskService` directly and never goes through `WorkerTaskController`'s HTTP gateway, so `contextMode='fat'` has no caller to prove yet. Building it now would mean editing `routing/api/WorkerTaskController.java`, which directly conflicts with this card's own Done-when (`git diff` on `routing/` must be EMPTY) — the empty-diff proof is the more important thing this card exists to demonstrate. Fat-claim stays a real TODO for whenever a genuine external runtime (`webhook`/`process`) is built and actually needs it; `WorkerTaskController`'s existing comment ("Fat-claim ContextBundle lands at M-AR1") is now known-stale and intentionally left untouched rather than edited (which would itself violate the empty-diff check) — flag it for whichever future session builds `webhook`/`process`.
✅ Done when: hire an agent with `runtimeType='echo'` → it claims and completes through the unmodified pipeline; **`git diff` on routing/, registry/ (minus seed), migrations = empty** — the M1.1-style proof for the runtime axis.

### M-LN2 — Learning surfaces in dashboard (NEW, frontend) · deps: M-LN1, M0.8
Review-queue tab on Escalations surface; "Learning" section in AgentProfile (memory counts, recent lessons, provenance links); memory browse/inspect panel; uses 16 endpoints only.
✅ Done when: reject→lesson→review→approve loop fully doable in UI, no API client needed.

---

## Test matrix additions (extends 08 §Testing must-test list)

| Invariant | Proven at |
|---|---|
| outbox row same-tx as state change | M0.3 |
| relay at-least-once, no loss on crash | M0.75 |
| attempt-scoped usage idempotency incl. requeue | M0.5b |
| consumer replay is a no-op (learn: keys) | M-LN1 |
| memory scope + status isolation (cross-agent, cross-tenant, pending/rejected excluded) | M-MEM1/M-LN1 |
| context budget respected + deterministic | M-CTX1 |
| new runtime with zero core diff | M-AR1 |
| budget soft-alert exactly-once + auto-pause/resume | M0.7 |
| decomposition fingerprint exact-once | M2.2 (07 card + 15 §3) |

## Config keys introduced (add to `.env.example` at each milestone)

`ANTHROPIC_API_KEY` (M0.5a) · `ATRIUM_LLM_TIMEOUT` (M0.5a) · `EMBEDDINGS_PROVIDER/EMBEDDINGS_MODEL/OPENAI_API_KEY` (M-MEM1) · `ATRIUM_MEMORY_TTL_DAYS=90` (M-MEM1) · `ATRIUM_LEARNING_MODEL_TIER=fast` (M-LN1) — plus 08 §Config originals.
