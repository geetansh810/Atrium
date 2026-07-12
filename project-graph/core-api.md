# core-api

**What:** Java 17 / Spring Boot 3.x / Maven service — **the source of truth** for all business state. Postgres + Redis. Everything else ([[web-dashboard]], [[office-realtime]]) is a projection of it.

**State: TASK WRITER LIVE (M0.1–M0.3 done, 2026-07-13 session 6).** Spring Boot 3.5.6 / Java 17 / Maven (wrapper vendored); 8 module packages + boundary docs; `common/` (TenantContext + header filter w/ company-creation exemption, RFC-7807 advice incl. FieldValidationException→fieldErrors, Ids/Clock, PageEnvelope + KeysetCursors pagination); Flyway V1+V2+V2_1 applied ([[data-model]]); compose stack boots health-UP; [[registry]] fully built (entities/endpoints/RuntimeRegistry/AgentDirectory/seed); [[routing]] writer built (tasks CRUD + TaskStateGuard + TaskEventRecorder) on the `eventbus` outbox (OutboxWriter, same-tx structural). 20 tests green. Next = M0.4 claim loop per `atrium-docs/17-backend-execution-plan.md`. Two new modules beyond the list below: `agentmind` (skills/memory/learning) and `eventbus` (transactional outbox → Redis relay).

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
