# core-api

**What:** Java 21 / Spring Boot 3.x / Maven service — **the source of truth** for all business state. Postgres + Redis. Everything else ([[web-dashboard]], [[office-realtime]]) is a projection of it.

**State: DASHBOARD ON REAL API (M0.1–M0.8 done, 2026-07-13 session 6h) — Phase 0's core loop is wired end to end, including the browser.** Spring Boot 3.5.6 / Java 21 (bumped from 17 at M0.5b — 12 §8/13 §3.2's virtual-thread agent runner needs it, doc-first fix across pom.xml/Dockerfile/08-conventions.md) / Maven (wrapper vendored); 8 module packages + boundary docs; `common/` (TenantContext + header filter w/ company-creation exemption AND an `OPTIONS`-preflight exemption added at M0.8 so CORS actually works, RFC-7807 advice incl. FieldValidationException→fieldErrors, Ids/Clock, PageEnvelope + KeysetCursors pagination, new `WebConfig` — CORS mapping for `/api/**`, origin from `atrium.cors.allowed-origins` default `http://localhost:5173`, added at M0.8 since core-api previously had zero CORS config and every browser call 400'd); Flyway V1+V2+V2_1+V3 applied ([[data-model]]); compose stack boots health-UP (⚠️ the built Docker image goes stale fast as source changes — `docker compose up -d --build`, not plain `up -d`, or you're running yesterday's migrations); [[registry]] built through lifecycle wiring + budget auto-pause (entities/endpoints/RuntimeRegistry/AgentDirectory/seed + AgentLifecycleService + RoleDefinitionLookup + `AgentDirectory.pauseForBudget`); [[routing]] built through the full lifecycle (tasks CRUD + TaskStateGuard + TaskEventRecorder + WorkBroker claim/claimNext/renew + LeaseReclaimJob + progress/complete/flag + approve/reject with rework-via-requeue) on the `eventbus` outbox; [[accountability]] has a real `BudgetGuard`/`BudgetLedger`; [[execution]] runs a real agent end-to-end; [[realtimebridge]] relays the outbox to Redis. **[[web-dashboard]] now reads/writes this API directly** (agents/tasks/budgets — chat/announcements/analytics still have no backend endpoint, stay mock-backed by design). 66 tests green (1 live test skipped w/o key). Next = M-SK1 skills registry (new `agentmind` module) per `atrium-docs/17-backend-execution-plan.md`.

**Modules (package-by-module, `app.atrium.*`, no circular imports, cross-module calls via service interfaces only):**
- [[registry]] — companies, users, role_definitions, agents
- [[routing]] — tasks, queues, claim/lease, subtasks
- [[execution]] — LlmClient abstraction, agent runner, prompt assembly
- [[accountability]] — budgets, approvals, task_events, analytics rollups
- [[realtimebridge]] — publishes events to Redis ([[realtime-events]])
- `common` — TenantContext, errors, config

**Conventions:** constructor injection, records for DTOs, no Lombok in entities, problem+json errors, UUIDs, TIMESTAMPTZ. Dev auth Phase 0–2 = `X-Company-Id`/`X-User-Id` headers → TenantContext.

**Contracts:** `atrium-docs/02-architecture.md` · [[data-model]] · `04-api-contract.md` · `05-module-specs.md`.

Links: [[_Atrium]] · [[data-model]] · [[realtime-events]] · [[milestones]]
