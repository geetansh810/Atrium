# core-api

**What:** Java 21 / Spring Boot 3.x / Maven service — **the source of truth** for all business state. Postgres + Redis. Everything else ([[web-dashboard]], [[office-realtime]]) is a projection of it.

**State: FIRST REAL AGENT LIVE (M0.1–M0.5b done, 2026-07-13 session 6c) — completes 07's M0.5.** Spring Boot 3.5.6 / Java 21 (bumped from 17 at M0.5b — 12 §8/13 §3.2's virtual-thread agent runner needs it, doc-first fix across pom.xml/Dockerfile/08-conventions.md) / Maven (wrapper vendored); 8 module packages + boundary docs; `common/` (TenantContext + header filter w/ company-creation exemption, RFC-7807 advice incl. FieldValidationException→fieldErrors, Ids/Clock, PageEnvelope + KeysetCursors pagination); Flyway V1+V2+V2_1 applied ([[data-model]]); compose stack boots health-UP; [[registry]] built through lifecycle wiring (entities/endpoints/RuntimeRegistry/AgentDirectory/seed + AgentLifecycleService + RoleDefinitionLookup); [[routing]] built through the worker loop's needs (tasks CRUD + TaskStateGuard + TaskEventRecorder + WorkBroker claim/claimNext/renew + LeaseReclaimJob + progress/complete/flag) on the `eventbus` outbox; [[accountability]] has the BudgetGuard interface (Noop until M0.7); [[execution]] runs a real agent end-to-end (LLM provider SPI + AnthropicClient + LlmRouter + cost math + LlmLoopRuntime + PromptAssembler + UsageRecorder). 50 tests green (1 live test skipped w/o key). Next = M0.6 approval gate & audit per `atrium-docs/17-backend-execution-plan.md`. Two new modules beyond the list below: `agentmind` (skills/memory/learning) and `eventbus` (transactional outbox → Redis relay).

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
