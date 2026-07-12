# core-api

**What:** Java 17 / Spring Boot 3.x / Maven service — **the source of truth** for all business state. Postgres + Redis. Everything else ([[web-dashboard]], [[office-realtime]]) is a projection of it.

**State: NOT STARTED, FULLY SPEC'D** (Rev C, 2026-07-12 — see [[agent-platform]]). MB-0 done (git repo on `main`, session 5); first code work = M0.1 per `atrium-docs/17-backend-execution-plan.md` (skeleton + Flyway V1+V2 + docker-compose with **pgvector/pgvector:pg16**, redis:7). Two new modules beyond the list below: `agentmind` (skills/memory/learning) and `eventbus` (transactional outbox → Redis relay).

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
