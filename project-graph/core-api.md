# core-api

**What:** Java 17 / Spring Boot 3.x / Maven service — **the source of truth** for all business state. Postgres + Redis. Everything else ([[web-dashboard]], [[office-realtime]]) is a projection of it.

**State: LLM SPI LIVE (M0.1–M0.5a done, 2026-07-13 session 6).** Spring Boot 3.5.6 / Java 17 / Maven (wrapper vendored); 8 module packages + boundary docs; `common/` (TenantContext + header filter w/ company-creation exemption, RFC-7807 advice incl. FieldValidationException→fieldErrors, Ids/Clock, PageEnvelope + KeysetCursors pagination); Flyway V1+V2+V2_1 applied ([[data-model]]); compose stack boots health-UP; [[registry]] fully built (entities/endpoints/RuntimeRegistry/AgentDirectory/seed); [[routing]] built through claim/lease (tasks CRUD + TaskStateGuard + TaskEventRecorder + WorkBroker claim/renew + LeaseReclaimJob) on the `eventbus` outbox (OutboxWriter, same-tx structural); [[accountability]] has the BudgetGuard interface (Noop until M0.7); [[execution]] has the full LLM provider SPI + AnthropicClient + LlmRouter + cost math. 48 tests green (1 live test skipped w/o key). Next = M0.5b agent runtime per `atrium-docs/17-backend-execution-plan.md`. Two new modules beyond the list below: `agentmind` (skills/memory/learning) and `eventbus` (transactional outbox → Redis relay).

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
