# 2 · Concepts & glossary

Short definitions, each with where it lives in code. Terms are alphabetical within groups.

## People and things

| Term | Meaning | Where |
|---|---|---|
| **Company** | The tenant. Every row of customer data belongs to exactly one. | [`registry/domain/Company.java`](../../core-api/src/main/java/app/atrium/registry/domain/Company.java) |
| **User** | A human member of a company; has a role claim on their JWT. Signup creates the company and its first admin. | [`registry/AuthService.java`](../../core-api/src/main/java/app/atrium/registry/AuthService.java) |
| **Agent** | An AI employee: role definition + skill tags + model/provider + manager + runtime + budget. Can be paused. | [`registry/domain/Agent.java`](../../core-api/src/main/java/app/atrium/registry/domain/Agent.java) |
| **Role definition** | Versioned system prompt + output contract + allowed tools. `company_id NULL` = a global template; a company can create its own. An agent is frozen to the version it was hired against. | [`registry/domain/RoleDefinition.java`](../../core-api/src/main/java/app/atrium/registry/domain/RoleDefinition.java) |
| **Role template** | A seeded global role definition: `coder`, `tester`, `research`, `lead`, `product`, `content`, `legal`, `hr`. | migrations `V2_1`, `V9`, `V12` in [`db/migration/`](../../core-api/src/main/resources/db/migration/) |
| **Skill tag** | A string on an agent (`"Coding"`, `"content"`) matched against a task's `required_skill`. Not the same as a *skill* below. | `agents.skill_tags` |

## Work

| Term | Meaning | Where |
|---|---|---|
| **Task** | A unit of work with a title, description, `required_skill`, priority, and a status. | [`routing/domain/Task.java`](../../core-api/src/main/java/app/atrium/routing/domain/Task.java) |
| **Status** | `queued → claimed → in_progress → pending_review → approved`, plus `flagged`, `rejected`, `cancelled`. Only the state guard moves it. | [`TaskStateGuard.java`](../../core-api/src/main/java/app/atrium/routing/domain/TaskStateGuard.java) |
| **Claim / lease** | An agent atomically takes a queued task and holds a 10-minute lease; an expired lease is requeued. | [`PostgresWorkBroker.java`](../../core-api/src/main/java/app/atrium/routing/PostgresWorkBroker.java), [`LeaseReclaimJob.java`](../../core-api/src/main/java/app/atrium/routing/LeaseReclaimJob.java) |
| **Attempt** | Claim counter; with the task id it forms the idempotency key `taskId:attempt`. | `tasks.attempt` |
| **Artifact** | The agent's output attached to a task. | [`routing/domain/Artifact.java`](../../core-api/src/main/java/app/atrium/routing/domain/Artifact.java) |
| **Subtask** | A human checklist item inside a task. Open subtasks block approval. | `subtasks` |
| **Child task** | A real task created by a decomposing agent (`parent_task_id`). Open children block the parent's approval. | [`ChildTaskTool.java`](../../core-api/src/main/java/app/atrium/execution/ChildTaskTool.java) |
| **Escalation** | A task needing a human: `flagged` or `pending_review`. | `GET /companies/{id}/escalations` |
| **Flag** | An agent could not finish (`max_attempts`, provider error, config incomplete…). Reasons: [`FlagReasons.java`](../../core-api/src/main/java/app/atrium/execution/FlagReasons.java). | |

## Money

| Term | Meaning | Where |
|---|---|---|
| **Budget / payroll** | Monthly token cap per agent, or company-wide (`agent_id NULL`). Opt-in: no row = uncapped. | [`accountability/BudgetService.java`](../../core-api/src/main/java/app/atrium/accountability/BudgetService.java) |
| **Usage record** | One row per billed LLM call, with tokens and cost in micro-USD, keyed idempotently. | [`execution/UsageRecorder.java`](../../core-api/src/main/java/app/atrium/execution/UsageRecorder.java) |
| **LLM request log** | Full prompt/response trace of every call, including failures; purged nightly. Debugging aid, **not** the billing ledger. | [`execution/LlmRequestLogService.java`](../../core-api/src/main/java/app/atrium/execution/LlmRequestLogService.java) |
| **Model catalog** | Seeded table of provider/model/tier/context/price. Hiring validates against it. | `model_catalog`, [`/model-catalog`](../../core-api/src/main/java/app/atrium/registry/api/ModelCatalogController.java) |

## The mind (agentmind module)

| Term | Meaning | Where |
|---|---|---|
| **Skill** | Versioned markdown procedure attached to a role or agent. Trust levels: `platform`, `company`, `agent_proposed` (inert in prompts). | [`agentmind/domain/Skill.java`](../../core-api/src/main/java/app/atrium/agentmind/domain/Skill.java) |
| **Memory** | A learned fact/lesson/preference/summary with scope (`agent`/`role`/`company`/`task`) and status (`active`/`pending_review`/…). | [`agentmind/domain/Memory.java`](../../core-api/src/main/java/app/atrium/agentmind/domain/Memory.java) |
| **Knowledge doc** | A reference document chunked and embedded, attached to a company role definition. | [`agentmind/KnowledgeService.java`](../../core-api/src/main/java/app/atrium/agentmind/KnowledgeService.java) |
| **Context bundle** | The skills + memories + knowledge chosen for one task, under a token budget. | [`agentmind/ContextBundle.java`](../../core-api/src/main/java/app/atrium/agentmind/ContextBundle.java) |
| **Provenance** | The ids of every skill/memory/knowledge row recalled, stored in the `claimed` event. | `task_events.payload.contextProvenance` |

## Infrastructure words

| Term | Meaning | Where |
|---|---|---|
| **Outbox** | `outbox_events` rows written in the same transaction as the change, then relayed to Redis and consumed by durable workers. | [`eventbus/`](../../core-api/src/main/java/app/atrium/eventbus/), [`realtimebridge/OutboxRelay.java`](../../core-api/src/main/java/app/atrium/realtimebridge/OutboxRelay.java) |
| **Durable consumer** | A polling worker with a persisted cursor (`event_consumers`): learning, stats rollup, chat notices. | [`EventCursorWorker.java`](../../core-api/src/main/java/app/atrium/eventbus/EventCursorWorker.java) |
| **Worker API** | The `X-Agent-Id`-authenticated claim/lease-renew gateway for external runtimes. | [`WorkerTaskController.java`](../../core-api/src/main/java/app/atrium/routing/api/WorkerTaskController.java) |
| **Runtime** | How an agent runs: `llm_loop` (built in) or `echo` (test). The SPI lives in `registry/runtime`. | [`LlmLoopRuntime.java`](../../core-api/src/main/java/app/atrium/execution/LlmLoopRuntime.java), [`EchoRuntime.java`](../../core-api/src/main/java/app/atrium/execution/EchoRuntime.java) |
| **Mock mode** | `VITE_USE_MOCKS=1`: the dashboard runs on JSON fixtures with no backend. | [`web/src/shared/config.ts`](../../web/src/shared/config.ts) |
| **Milestone ids** | `M0.x`, `M-SK1`, `UX1`… appear in code comments and doc 07; each is a build session with a Done-when. | [`07-milestones.md`](../07-milestones.md) |
