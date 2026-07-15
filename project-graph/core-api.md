# core-api

**What:** Java 21 / Spring Boot 3.x / Maven service — **the source of truth** for all business state. Postgres + Redis. Everything else (e.g. [[web-dashboard]]) is a projection of it.

**State: current test count 127/127 green, 3 skipped (2026-07-15, session 17, M-KN1) — see [[agentmind]] for the latest milestone (knowledge ingestion — the skills→context→memory→learning→knowledge depth series is now complete). This paragraph is otherwise unchanged since M0.8.**

DASHBOARD ON REAL API (M0.1–M0.8 done, 2026-07-13 session 6h) — Phase 0's core loop is wired end to end, including the browser. Spring Boot 3.5.6 / Java 21 (bumped from 17 at M0.5b — 12 §8/13 §3.2's virtual-thread agent runner needs it, doc-first fix across pom.xml/Dockerfile/08-conventions.md) / Maven (wrapper vendored); 8 module packages + boundary docs; `common/` (TenantContext + header filter w/ company-creation exemption AND an `OPTIONS`-preflight exemption added at M0.8 so CORS actually works, RFC-7807 advice incl. FieldValidationException→fieldErrors, Ids/Clock, PageEnvelope + KeysetCursors pagination, new `WebConfig` — CORS mapping for `/api/**`, origin from `atrium.cors.allowed-origins` default `http://localhost:5173`, added at M0.8 since core-api previously had zero CORS config and every browser call 400'd); Flyway V1+V2+V2_1+V3+V4 applied ([[data-model]] — V4 adds Gemini rows to `model_catalog`); compose stack boots health-UP (⚠️ the built Docker image goes stale fast as source changes — `docker compose up -d --build`, not plain `up -d`, or you're running yesterday's migrations); [[registry]] built through lifecycle wiring + budget auto-pause (entities/endpoints/RuntimeRegistry/AgentDirectory/seed + AgentLifecycleService + RoleDefinitionLookup + `AgentDirectory.pauseForBudget`); [[routing]] built through the full lifecycle (tasks CRUD + TaskStateGuard + TaskEventRecorder + WorkBroker claim/claimNext/renew + LeaseReclaimJob + progress/complete/flag + approve/reject with rework-via-requeue) on the `eventbus` outbox; [[accountability]] has a real `BudgetGuard`/`BudgetLedger`; [[execution]] runs a real agent end-to-end with **two live LlmProvider beans** — `AnthropicClient` (official SDK) and `GoogleClient` (plain REST via Spring `RestClient`, no SDK dependency added — added post-M0.8 and it's what finally ran M0.8's full claim→work→approve loop live on Gemini's free tier, `gemini-3.1-flash-lite`; 13 §1's SPI made this a pure addition, zero changes to `LlmRouter`/`LlmLoopRuntime`. docker-compose now forwards the provider keys into the container — it previously forwarded none); [[realtimebridge]] relays the outbox to Redis. **[[web-dashboard]] now reads/writes this API directly** (agents/tasks/budgets — chat/announcements/analytics still have no backend endpoint, stay mock-backed by design). 78 tests green (2 live tests skipped w/o keys).

**Since (full detail: [[agentmind]]):** M-SK1 (2026-07-14 session 13) added the `agentmind` module — skills registry, hire-time auto-attach, `/mind` view. M-CTX1 (2026-07-14 session 14) added `ContextAssembler` (skills-only v1) wired into [[execution]]'s claim→prompt pipeline, with `task_events(claimed).payload.contextProvenance` written same-tx as the claim. M-MEM1 (session 15) added memory store + recall; M-LN1 (session 16) added the learning pipeline + review governance; M-KN1 (session 17) added knowledge ingestion, the last piece — `ContextBundle`'s three slots (skills/memories/knowledge) are all populated now, and `PromptAssembler` renders all three sections. 127 tests green (3 live tests skipped w/o keys). Next = M-AR1 (runtime extensibility proof) or M-LN2 (learning surfaces in the dashboard) per `atrium-docs/17-backend-execution-plan.md` — both available now, independent of each other.

**Modules (package-by-module, `app.atrium.*`, no circular imports, cross-module calls via service interfaces only):**
- [[registry]] — companies, users, role_definitions, agents
- [[routing]] — tasks, queues, claim/lease, subtasks
- [[execution]] — LlmClient abstraction, agent runner, prompt assembly
- [[accountability]] — budgets, approvals, task_events, analytics rollups
- [[realtimebridge]] — publishes events to Redis ([[realtime-events]])
- [[agentmind]] — skills registry, ContextAssembler (memory/knowledge/learning to come)
- `common` — TenantContext, errors, config

**Conventions:** constructor injection, records for DTOs, no Lombok in entities, problem+json errors, UUIDs, TIMESTAMPTZ. Dev auth Phase 0–2 = `X-Company-Id`/`X-User-Id` headers → TenantContext.

**Contracts:** `atrium-docs/02-architecture.md` · [[data-model]] · `04-api-contract.md` · `05-module-specs.md`.

Links: [[_Atrium]] · [[data-model]] · [[realtime-events]] · [[milestones]] · [[agentmind]]
