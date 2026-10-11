# 4 · Architecture map

For depth and tradeoffs read [`18-technical-architecture.md`](../18-technical-architecture.md) §§2–3; this page is the map.

## The picture

```
 Browser (React SPA)  ── HTTPS + Bearer JWT ──►  core-api (Spring Boot 3, Java 21)
   web/                                            │  modular monolith, 9 packages
                                                   │
        ┌──────────────────────────────────────────┤
        ▼                                          ▼
   Postgres 16 + pgvector                       Redis 7
   system of record, RLS,                       outbox relay pub/sub,
   outbox table                                 rate-limit counters
        ▲
        │ claim / complete / audit
   one virtual thread per active agent (llm_loop) ── LLM providers (Anthropic, Google)
                                                 └── embeddings (OpenAI, optional)
```

Single deployable backend, one database, no message broker beyond Redis. Scale-out reasoning: [`12-backend-architecture.md`](../12-backend-architecture.md) §8.

## Repo layout

| Path | What |
|---|---|
| [`core-api/`](../../core-api/) | Spring Boot backend, Flyway migrations in `src/main/resources/db/migration/`, tests in `src/test/` |
| [`web/`](../../web/) | Dashboard; `src/pages` (screens), `src/ui` (primitives), `src/shared` (api, stores, selectors), `src/shell` (nav + routes) |
| [`landing/`](../../landing/) | Static marketing page (the in-app one is `web/src/pages/landing/`) |
| [`atrium-docs/`](../) | Contracts 00–17, as-built doc 18, this handbook, compliance, notes |
| [`project-graph/`](../../project-graph/) | Wiki-linked context graph; start at `_Atrium.md` |
| [`infra/terraform/`](../../infra/terraform/) | AWS IaC (validated, never applied) |
| [`loadtest/`](../../loadtest/) | k6 load test; `.github/` has CI, deploy workflows and the smoke test |

## Backend modules

Package-by-module under [`app.atrium`](../../core-api/src/main/java/app/atrium/). Each package has a `package-info.java` stating its boundary.

| Module | Owns | Entry points |
|---|---|---|
| `common` | Tenant context + filters, JWT, rate limit, errors, pagination, logging context | [`TenantContext`](../../core-api/src/main/java/app/atrium/common/TenantContext.java), [`TenantContextFilter`](../../core-api/src/main/java/app/atrium/common/TenantContextFilter.java) |
| `registry` | Companies, users/auth, agents, role definitions, model catalog, the runtime SPI | [`AgentService`](../../core-api/src/main/java/app/atrium/registry/AgentService.java), [`AuthService`](../../core-api/src/main/java/app/atrium/registry/AuthService.java) |
| `routing` | Tasks, state machine, claim/lease, approval gate, decomposition, flow graph | [`TaskService`](../../core-api/src/main/java/app/atrium/routing/TaskService.java), [`PostgresWorkBroker`](../../core-api/src/main/java/app/atrium/routing/PostgresWorkBroker.java) |
| `execution` | Agent loop, LLM router + providers, prompt assembly, usage recording, LLM logs | [`LlmLoopRuntime`](../../core-api/src/main/java/app/atrium/execution/LlmLoopRuntime.java), [`LlmRouter`](../../core-api/src/main/java/app/atrium/execution/LlmRouter.java) |
| `accountability` | Budgets, usage ledger, analytics rollups | [`BudgetService`](../../core-api/src/main/java/app/atrium/accountability/BudgetService.java), [`StatsRollupWorker`](../../core-api/src/main/java/app/atrium/accountability/StatsRollupWorker.java) |
| `agentmind` | Skills, memory, knowledge, learning pipeline, context assembly | [`SkillContextAssembler`](../../core-api/src/main/java/app/atrium/agentmind/SkillContextAssembler.java), [`LearningPipeline`](../../core-api/src/main/java/app/atrium/agentmind/LearningPipeline.java) |
| `communication` | Channels, messages, announcements, completion chat notices | [`ChannelService`](../../core-api/src/main/java/app/atrium/communication/ChannelService.java) |
| `eventbus` | Outbox writer, relay gateway, durable-consumer base class, topics | [`OutboxWriter`](../../core-api/src/main/java/app/atrium/eventbus/OutboxWriter.java) |
| `realtimebridge` | Outbox → Redis publisher | [`OutboxRelay`](../../core-api/src/main/java/app/atrium/realtimebridge/OutboxRelay.java) |

## Who may call whom

The rules are in [`12-backend-architecture.md`](../12-backend-architecture.md) §2. In practice:

- **Never reach into another module's repository.** Cross-module reads go through a small interface owned by one side: `AgentDirectory`, `ModelCatalogLookup`, `RoleDefinitionLookup` (registry), `BudgetGuard`/`BudgetLedger`/`UsageLedger` (accountability), `WorkBroker` (routing), `ContextAssembler` (agentmind), `RoleSkillAttachment` (owned by registry, implemented by agentmind).
- **`agentmind` and `accountability` must not import `app.atrium.execution`** (except `execution.spi`, the neutral LLM facade). That is why usage metering is an interface in accountability that both the agent loop and the learning pipeline call.
- **`routing` imports neither `execution` nor `agentmind`.** The one place routing needs agentmind output (recall provenance in the `claimed` event) gets it as a plain `Function<Task, ObjectNode>` passed into `claimNext`.
- **Events are one-way.** State changes append to the outbox in the same transaction ([`OutboxWriter`](../../core-api/src/main/java/app/atrium/eventbus/OutboxWriter.java), propagation `MANDATORY`); consumers (learning, stats, chat) read the outbox and never call back into the writer's module.

## Three extension axes

1. **LLM provider** — implement `LlmProvider` ([`execution/spi/`](../../core-api/src/main/java/app/atrium/execution/spi/)), add a catalog row. [`GoogleClient`](../../core-api/src/main/java/app/atrium/execution/GoogleClient.java) was added without touching the router.
2. **Agent runtime** — implement `AgentRuntime` ([`registry/runtime/`](../../core-api/src/main/java/app/atrium/registry/runtime/)). [`EchoRuntime`](../../core-api/src/main/java/app/atrium/execution/EchoRuntime.java) was added with zero changes to routing or registry.
3. **Role / skill / knowledge** — rows, not code. A new capability is a `role_definition` plus skill tags. `if (skill == …)` in routing is a bug by definition.

*Verified against:* the `package-info.java` of each module, [`12-backend-architecture.md`](../12-backend-architecture.md) §2, [`docker-compose.yml`](../../docker-compose.yml).
