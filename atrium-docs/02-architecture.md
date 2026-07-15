# 02 — Architecture
> **Office track removed 2026-07-15** — the SkyOffice virtual office (M2.4a–c, office-realtime, office_layout, /office-state) was retired and replaced by the live Team view (`/team`) in the dashboard; see `07-milestones.md`. Office references below are historical.

## 1. System overview

Three services, one monorepo. **core-api owns all business truth.** office-realtime and web are projections of it.

```
┌─────────────────────────────────────────────────────────────────┐
│                          web (React)                            │
│   /dashboard  (roster, tasks, workspace, analytics, budgets)    │
│   /office     (forked SkyOffice Phaser 3 client, embedded)      │
└───────────────┬────────────────────────────┬────────────────────┘
                │ REST (JSON)                │ WebSocket (Colyseus)
┌───────────────▼──────────────┐   ┌─────────▼────────────────────┐
│    core-api (Spring Boot)    │   │ office-realtime (Colyseus TS)│
│  registry | routing |        │◄──┤  presence, movement, chat    │
│  execution | accountability  │   │  rooms; agent avatar driver  │
│                              │──►│  (reads state, renders it)   │
└───────┬──────────────┬───────┘   └──────────────────────────────┘
        │              │                     ▲
   ┌────▼────┐    ┌────▼────┐         Redis pub/sub channel
   │Postgres │    │  Redis  │─────────(state-change events)
   └─────────┘    └─────────┘
```

## 2. Monorepo layout

```
atrium/
├── docs/                      # this documentation package
├── core-api/                  # Java 21 / Spring Boot 3.x, Maven
│   └── src/main/java/app/atrium/
│       ├── registry/          # companies, agents, role templates
│       ├── routing/           # tasks, queues, claim/lease, subtasks
│       ├── execution/         # LLM provider abstraction, agent runner, prompts
│       ├── accountability/    # budgets, approvals, task_events, analytics rollups
│       ├── realtimebridge/    # publishes state-change events to Redis pub/sub
│       └── common/            # tenant context, errors, config
│   └── src/main/resources/db/migration/   # Flyway V1__, V2__, ...
├── office-realtime/           # forked SkyOffice server/ (Colyseus, TS)
├── web/                       # React app
│   ├── src/dashboard/         # panels: roster, tasks, workspace, analytics, budget
│   ├── src/office/            # forked SkyOffice client/ (Phaser 3 + Redux slice)
│   └── src/shared/            # api client, theme.ts, types
├── shared-types/              # TS types generated/mirrored from API contract
├── docker-compose.yml         # postgres, redis, all three services
└── Makefile                   # make dev, make test, make check
```

**Module boundary rule:** `registry`, `routing`, `execution`, `accountability` are separate Java packages with no circular imports; cross-module calls go through service interfaces. This is what keeps "add a role without touching the router" true and lets LLM sessions work on one module without loading the world.

## 3. Data flow — the life of a task

1. **Create:** `POST /companies/{id}/tasks` (web → core-api). Routing validates skill exists in registry for that company, inserts `tasks` row (`status=queued`), appends `task_events(created)`, publishes `task.created` on Redis.
2. **Claim:** Agent runner workers poll per skill queue via `FOR UPDATE SKIP LOCKED` claim query. Winner sets `status=claimed`, `lease_expires_at=now()+10min`, appends `task_events(claimed)`, publishes `agent.status_changed(working)`.
3. **Work:** execution module builds the role-scoped prompt (role definition + task + relevant context; never secrets), calls the provider through the LLM abstraction, streams token usage into `usage_records`, updates subtask states/progress, appends events (`progress`, `note`). Long tasks renew the lease.
4. **Escalate (optional):** blocked/uncertain → `status=flagged`, event `flagged(reason)`, publish `agent.status_changed(in_meeting)` → avatar walks to Meeting Room; escalation surface lights up.
5. **Complete:** output stored as an `artifacts` row, `status=pending_review`, event `completed`, notify assigner (chat message from agent + Atrium Bot).
6. **Review:** human `POST /tasks/{id}/approve|reject`. Approve → `approved`, agent success-rate rollup updates, avatar returns to desk/idle. Reject → feedback stored, back to `in_progress`.
7. **Budget:** every LLM call inserts `usage_records(tokens, cost)`; claim step re-checks `spent < cap`; over-cap blocks the claim and flags.

## 4. Realtime bridge (core-api → office)

core-api publishes compact JSON events on Redis pub/sub channel `atrium:events:{companyId}`:

```json
{ "type": "agent.status_changed", "agentId": "…", "status": "working",
  "activity": "Building API endpoint", "taskId": "…", "ts": "…" }
{ "type": "task.progress", "taskId": "…", "progress": 65, "eta_minutes": 20 }
{ "type": "task.flagged", "taskId": "…", "agentId": "…", "reason": "…" }
{ "type": "chat.message", "channel": "#general", "from": "agent:…", "text": "…" }
```

office-realtime subscribes, translates events into Colyseus room-state mutations (avatar target position from the status→location map, speech-bubble text, status dots), and Phaser renders. **office-realtime holds no durable state**; on restart it rebuilds from `GET /companies/{id}/office-state`.

Status → location map (data, not code — lives in a config table so the office layout can change):
`working→assigned desk · in_meeting→meeting_room_alpha · in_focus→focus_pod_n · away→cafe · flagged→help_desk · offline→despawn`.

## 5. Tenancy model

- Every business table carries `company_id NOT NULL`; every repository method takes tenant context from the authenticated principal (Phase 0–2: a header/dev-mode company; Phase 3: JWT claim). A Hibernate filter + Postgres RLS (Phase 3) enforce it twice.
- One Colyseus room per company (`office:{companyId}`); join requires a core-api-issued room token. No cross-company rooms, ever.
- Redis keys and channels are always company-prefixed.

## 6. LLM provider abstraction

```java
interface LlmClient {
  LlmResult complete(LlmRequest r); // provider, model, messages, maxTokens, tools
}
// impls: AnthropicClient, OpenAiClient, GeminiClient — selected per agent row.
// Every call returns tokensIn/tokensOut and is recorded by accountability.
```
Agent "depth" = role definition record: system prompt template, allowed tools, knowledge refs, output contract. Stored in `role_definitions`, versioned — changing a role never redeploys code.

## 7. Deployment

- Dev: `docker compose up` (postgres, redis, core-api, office-realtime, web).
- Prod (Phase 3): AWS — ECS/Fargate containers, RDS Postgres, ElastiCache Redis, ALB with sticky sessions for Colyseus WebSockets, S3+CloudFront for the web build. One environment first; staging when there are outside users.

## 8. Non-goals of this architecture (v1)
- No Kafka/RabbitMQ until throughput demands it (the Postgres queue is the v1 broker; the routing module's interface makes swapping later a contained change).
- No microservice split of core-api. Modular monolith, on purpose.
- No event sourcing beyond task_events; it's an audit log, not the write model.
