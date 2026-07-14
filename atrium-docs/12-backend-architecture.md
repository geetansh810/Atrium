# 12 — Backend Architecture (Rev C: event-driven agent platform)

Extends `02-architecture.md`. Where they conflict, this file wins for backend work. Pattern provenance: `notes/paperclip-findings.md` (adapter SPI, skills, memory, budgets) and `notes/solace-agent-mesh-findings.md` (topics, outbox-as-mesh, gateways, orchestrator-as-agent). Companion contracts: `13-llm-and-agent-spi.md`, `14-skills-memory-learning.md`, `15-data-model-delta.md`, `16-api-contract-delta.md`. Build order: `17-backend-execution-plan.md`.

## 1. Design goals (the three pluggability axes)

Everything below exists to make these three sentences permanently true **without schema breaks**:

1. **Any LLM**: adding a provider = one adapter class + `model_catalog` rows. Adding a model = rows only. Agents point at `(model_provider, model_name)`; nothing else in the system knows provider names.
2. **Any agent type**: an agent is a roster row with `runtime_type` + `runtime_config`. The built-in `llm_loop` runtime is just the first implementation of the `AgentRuntime` SPI (invoke/status/cancel — Paperclip's minimum contract). External webhook/process agents plug in later with zero migration.
3. **Any depth of specialization**: what an agent *knows and can do* is data — `role_definitions` (identity + output contract) + `skills` (versioned procedures) + `memories` (per-tenant learned facts) + `knowledge` (retrievable documents). Training an agent for a role = attaching/curating rows. See 14.

## 2. Module map (modular monolith, package-by-module — unchanged rule set)

```
core-api/src/main/java/app/atrium/
├── common/           TenantContext, ProblemJson errors, config, Ids, Clock
├── registry/         companies, users, role_definitions, agents, model_catalog,
│                     AgentDirectory (capability registry = our "AgentCards")
├── routing/          tasks, subtasks, queues, claim/lease/reclaim, task graph,
│                     WorkBroker interface (Postgres impl v1)
├── execution/        LlmClient SPI + providers, AgentRuntime SPI + LlmLoopRuntime,
│                     PromptAssembler (pure)
├── agentmind/        skills registry, ContextAssembler (reads skills/memory —
│                     lives here, not execution: agentmind must never import
│                     execution types, only the reverse), MemoryStore SPI +
│                     PgVectorMemoryStore, EmbeddingClient, LearningPipeline,
│                     knowledge ingestion   ← NEW module
├── accountability/   budgets, BudgetGuard, UsageRecorder, approvals, task_events
│                     read API, StatsRollup, Atrium Bot notices
├── eventbus/         DomainEvents, OutboxWriter, OutboxRelay, consumer cursors ← NEW module
└── realtimebridge/   Redis publisher fed by eventbus (office projection)
```

Boundary rules (unchanged + additions):
- Cross-module calls only via service interfaces; no circular imports. Dependency direction: `routing → registry`, `routing → accountability (BudgetGuard)`, `execution → routing (TaskService)`, `execution → agentmind (ContextAssembler)`, `agentmind → registry`, `eventbus ← everyone (write-only)`, `realtimebridge → eventbus (read-only)`.
- `agentmind` never writes task state. `eventbus` has no business logic. `if (skill == …)` anywhere in routing remains a bug by definition.

## 3. Event backbone: transactional outbox (the "mesh" sized for v1)

02 §8 stands: no Kafka/Rabbit/Solace in v1. But event-driven is a *shape*, not a broker purchase. The shape:

1. **Every state change appends a domain event in the same transaction** — we already do this for audit (`task_events`). Additionally, changes any consumer cares about write one row to `outbox_events` (15 §2) in that same transaction: `(topic, event_type, payload, company_id)`.
2. **OutboxRelay** (@Scheduled, 250ms batch, `FOR UPDATE SKIP LOCKED` on unpublished rows — same idiom as task claiming) publishes each row to Redis and stamps `published_at`. At-least-once; consumers must be idempotent. Relay failure = events delay, business truth unaffected.
3. **Two consumer styles:**
   - *Projection consumers* (lossy-OK): realtimebridge relays to Redis pub/sub `atrium:events:{companyId}` for office/dashboard. Missed events self-heal via `GET /office-state` (unchanged contract).
   - *Durable consumers* (must-process): in-process workers with a cursor row in `event_consumers` (`consumer_name, last_event_id`) polling `outbox_events` beyond their cursor. v1 durable consumers: `LearningPipeline` (14 §5), `StatsRollup` (on approve/reject). Exactly-once effect via idempotency keys, not delivery guarantees.
4. **Broker swap later** = re-implement OutboxRelay against Kafka/Solace and move durable consumers to real subscriptions. Producers and payloads never change. This is the SAM upgrade path with zero v1 cost.

### 4. Topic taxonomy (canonical event addresses)

Every outbox row carries a `topic` (SAM-style, future-broker-ready) and an `event_type` (existing dot.case). Redis channel remains `atrium:events:{companyId}` (contract in 04 unchanged); `topic` is stored for durable consumers + the future broker.

```
atrium/v1/{companyId}/task/{taskId}            task.created|claimed|progress|flagged|completed|approved|rejected|requeued|cancelled
atrium/v1/{companyId}/agent/{agentId}          agent.status_changed|agent.bubble|agent.hired|agent.updated
atrium/v1/{companyId}/budget/{agentId|company} budget.threshold|budget.exceeded          ← soft alert tier (Paperclip)
atrium/v1/{companyId}/memory/{agentId}         memory.learned|memory.promoted|memory.review_requested
atrium/v1/{companyId}/chat/{channelId}         chat.message
atrium/v1/{companyId}/system                   announcement.created|bot.notice
```

Payloads: compact JSON per 04 §WebSocket + 16 §4 additions. Rules: publish only via OutboxWriter (in-tx); `company_id` always present; consumers never mutate business state except through module service interfaces.

## 5. Gateways (named extension points, v1 = REST only)

SAM pattern: a gateway translates an external protocol into internal commands and consumes the event stream — it owns authn, session mapping, and delivery. Atrium gateways:

| Gateway | Protocol | Status |
|---|---|---|
| REST API (04-contract) | HTTP/JSON | v1 — the human gateway |
| Worker API (04 §Tasks claim/progress/complete) | HTTP/JSON | v1 — the *agent* gateway; external runtimes use exactly these endpoints |
| office-realtime | Colyseus WS | Phase 2 (projection-only, not a command gateway) |
| Slack / email / cron triggers | future | new module each; must depend only on 04/16 endpoints + topic taxonomy |

Rule: a gateway never imports module internals. If a gateway needs a capability the API lacks, the contract (04/16) changes first.

## 6. Orchestration is a role, not infrastructure

Manager/PM agents decompose parent tasks into child tasks via the normal task API (M2.2). SAM validates this shape; Paperclip supplies the safety rail: **exact-once decomposition** — child-tree creation for an accepted plan is fingerprinted by `(parent_task_id, plan_artifact_id)` with a unique index (15 §3), so a re-run/re-wake can never fan out twice. Parent approval stays blocked while children are open (invariant 5). Cost attribution: child tasks carry `billing_task_id` = root of the request chain (15 §3), so delegated work rolls up (Paperclip billing codes).

## 7. The life of a task (delta over 02 §3)

Steps 1–7 of 02 §3 stand. Changes:
- Every step's event now also writes `outbox_events` (same tx) — realtimebridge publishes from the relay, not from `@TransactionalEventListener` (latency budget: relay ≤250ms poll, well inside M2.4c's 2s Done-when).
- Step 3 (Work) becomes: runtime = `AgentRuntime` for the agent row; for `llm_loop`: claim → **ContextAssembler.assemble(agent, task)** (skills + memories + knowledge under a token budget, 14 §6) → **PromptAssembler.build(roleDef, task, feedback?, contextBundle?)** (still a pure function of its inputs — secrets structurally impossible) → LlmClient → UsageRecorder (idempotency `taskId:attempt`) → progress/complete/flag.
- New step 8 (Learn): on `task.rejected`/`task.approved`, LearningPipeline (durable consumer) extracts lessons/facts into `memories` with provenance; company/role-scope writes queue for human review (14 §5). Nothing ships unreviewed — including what agents learn.

## 8. Scalability path (decide by evidence, in this order)

1. **v1**: one core-api instance; AgentRunner on virtual threads (one loop per active agent); Postgres queue. Comfortably hundreds of agents/tenant-cluster.
2. **Vertical + read paths**: indexes are specified in 03/15; analytics reads hit rollups, never raw events.
3. **Horizontal core-api**: N instances are already safe — claim is SKIP LOCKED, relay is SKIP LOCKED, reclaim/rollup jobs take a Postgres advisory lock (`pg_try_advisory_lock`) so exactly one instance runs each singleton job. No new infra.
4. **Split workers**: run instances with `--spring.profiles.active=worker` (runners + relay only, no HTTP) vs `api` (HTTP only). Same image, config split — this is the "separate execution plane" moment.
5. **Real broker**: only when outbox throughput or fan-out demands it (§3.4).

## 9. Failure & recovery model

- LLM/provider failure → `flag(reason)`, never crash a runner (unchanged). Error taxonomy in 13 §5 maps to flag reasons.
- Missing provider key / config → **pre-dispatch validation**: runner refuses to start the agent's loop and flags `config_incomplete` (Paperclip pre-dispatch gate) — never a dispatched-then-failed call.
- Lease expiry → automatic requeue with `task_events(requeued)` — automatic but never silent.
- Claim conflict (409) → worker moves on; **never retries the same claim** (Paperclip rule).
- Relay/consumer crash → events wait in outbox; cursors resume; idempotency keys make reprocessing harmless.
- Redis down → office degrades to bootstrap snapshots; core-api unaffected.

## 10. Hard rules (additions to CLAUDE.md set — enforce in review)

1. Outbox row written in the same tx as the state change it describes; no direct Redis publishes from business code.
2. Durable consumers are idempotent; every side effect carries an idempotency key.
3. `agentmind` writes are governed: agent-scope lessons auto-activate; role/company-scope memories require human approval before entering any prompt.
4. ContextAssembler reads ONLY: skills, memories (status='active'), knowledge chunks, role_definitions. It is the single doorway from stored knowledge into prompts.
5. Singleton scheduled jobs guard with advisory locks from day one (cheap now, mandatory at step 8.3).
