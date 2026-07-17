# 04 — API Contract
> **Office track removed 2026-07-15** — the SkyOffice virtual office (M2.4a–c, office-realtime, office_layout, /office-state) was retired and replaced by the live Team view (`/team`) in the dashboard; see `07-milestones.md`. Office references below are historical.

> **Rev C:** `16-api-contract-delta.md` adds skills/memory/review-queue/model-catalog endpoints, new agent fields (`runtimeType`, `runtimeConfig`, `paused`), new event payloads, and names the claim/progress/complete set below as the **Worker API gateway** for external agent runtimes. Read 16 alongside this file.

Base URL: `/api/v1`. JSON everywhere. Errors: RFC-7807 `application/problem+json`. **Auth (M3.1, 2026-07-16): `X-Company-Id`/`X-User-Id` dev headers are gone.** Every `/api/**` route (except `/api/v1/auth/signup`, `/api/v1/auth/login`, and the Worker API gateway below) requires `Authorization: Bearer <JWT>` (claims: `sub`=userId, `companyId`, `role`, HS256, `atrium.jwt.secret`/`ATRIUM_JWT_SECRET`) — endpoint shapes below are otherwise unchanged. The Worker API gateway (`/tasks/{id}/claim`, `/tasks/{id}/lease/renew`) is a separate auth axis — `X-Agent-Id` only, no human bearer token — since it resolves its own tenant from the agent id (registry `AgentDirectory.companyIdOf`).

## Auth

| Method | Path | Body → Response |
|---|---|---|
| POST | `/auth/signup` | `{companyName, companySlug, displayName, email, password}` → `{token, companyId, userId, role, companyName, companySlug}` — creates the company AND its first admin user atomically; the one pre-auth bootstrap route |
| POST | `/auth/login` | `{email, password}` → same shape as signup |

Passwords: BCrypt (`spring-security-crypto`), never logged, never returned. JWT expiry: 30 days (no refresh-token flow this milestone — a real production posture would want shorter-lived tokens + refresh; deferred to M3.5). `role` is carried on the token but nothing gates on it yet (single-admin-per-company reality so far — RBAC enforcement is a future card, not this one's scope).

## Registry

| Method | Path | Body → Response |
|---|---|---|
| GET | `/companies/{id}` | → Company (company creation now only via `POST /auth/signup` above — M3.1 removed the standalone bootstrap route since every company must have an owning admin user) |
| POST | `/companies/{id}/agents` | `{name, spriteKey, roleDefinitionId?, roleTemplateKey?, roleTitle, skillTags[], modelProvider, modelName, managerAgentId?, about?}` → Agent |
| GET | `/companies/{id}/roster` | → `Agent[]` (with live status, current activity line) |
| PATCH | `/agents/{id}` | partial update → Agent |
| GET | `/agents/{id}/profile` | → `{agent, stats:{tasksCompleted, successRate, focusMinutes}, skills[], currentTasks[], activityFeed[]}` |
| GET/POST | `/role-definitions` | list global+company templates / create custom — create body gains optional `reviewRequired` (default false, M4.1: 403s approve unless a named human signs off, see below) |

## Tasks & workflow

| Method | Path | Notes |
|---|---|---|
| POST | `/companies/{id}/tasks` | `{title, description?, requiredSkill, priority?, etaMinutes?, parentTaskId?, subtasks?: [{label}]}` → Task |
| GET | `/companies/{id}/tasks?status=&skill=&agentId=&view=my|assigned|completed` | task board / workspace tabs |
| GET | `/tasks/{id}` | → Task + subtasks + latest artifact |
| POST | `/tasks/{id}/claim` | worker-only; runs the canonical claim query |
| POST | `/tasks/{id}/lease/renew` | worker heartbeat |
| POST | `/tasks/{id}/progress` | `{progress, etaMinutes?, note?, subtaskUpdates?:[{id,state}]}` |
| POST | `/tasks/{id}/flag` | `{reason}` → status=flagged |
| POST | `/tasks/{id}/complete` | `{artifact:{kind,content}}` → status=pending_review |
| POST | `/tasks/{id}/approve` | human/supervisor; blocked if open children/subtasks; 403 if the assigned agent's role has `reviewRequired=true` and the caller isn't a named human (`TenantContext.userId()` — M4.1, 07 Phase 4) |
| POST | `/tasks/{id}/reject` | `{feedback}` → back to in_progress |
| GET | `/tasks/{id}/events` | full audit trail |
| GET | `/tasks/{id}/flow` | parent/children graph for the Task Flow view — `{id}` may be ANY task in the chain: resolves the root ancestor first, then returns `{nodes:[{taskId,title,status,agent}], edges:[{from,to}]}` for every task reachable from that root (M2.2) |
| GET | `/companies/{id}/escalations` | all flagged/pending_review, newest first |

## Accountability

| Method | Path | Notes |
|---|---|---|
| GET | `/companies/{id}/budget?period=YYYY-MM` | caps + spend, company & per-agent |
| PUT | `/companies/{id}/budget` | `{agentId?, period, capTokens}` |
| GET | `/companies/{id}/analytics/summary` | KPI cards: `{tasksCompletedToday, tasksApprovedToday, tasksRejectedToday, tokensSpentToday, costMicroUsdToday, successRateAllTime}` |
| GET | `/companies/{id}/analytics/tasks-7d` | bar-chart series, `[{day:"YYYY-MM-DD", count}]`, oldest first |
| GET | `/companies/{id}/analytics/agent-performance?days=` (default 7) | leaderboard, `[{agentId, tasksCompleted, tasksApproved, tasksRejected, successRate, tokensSpent, costMicroUsd}]` desc by tasksCompleted — bare `agentId`, frontend resolves name from its own roster fetch (same join pattern as `/budget`'s bare `agentId`) |
| GET | `/companies/{id}/analytics/top-skills?days=` (default 7) | skill-share chips, `[{skill, tasksCompleted, sharePct}]` desc by tasksCompleted |
| GET | `/companies/{id}/analytics/cost-per-task?period=YYYY-MM&limit=` (default 10) | **M2.3 addition, not in the original 17 sketch** — real per-task spend for the Budget Ledger's cost table, `[{taskId, tokens, costMicroUsd}]` desc by cost. Queries `usage_records` directly (grouped by `task_id`), not `agent_stats_daily` — a per-task number can't come from a table keyed one row per (agent, skill, day) |

## Communication

| Method | Path | Notes |
|---|---|---|
| GET/POST | `/companies/{id}/channels` | list/create |
| GET | `/channels/{id}/messages?before=&limit=` | history |
| POST | `/channels/{id}/messages` | `{text}` (user send; agent/bot messages come from core-api internally) |
| GET/POST | `/companies/{id}/announcements` | list/create |

## Office

| Method | Path | Notes |
|---|---|---|
| GET | `/companies/{id}/office-state` | full snapshot: agents + status + location + activity + bubbles (office-realtime bootstrap) |
| POST | `/companies/{id}/office/room-token` | short-lived token to join Colyseus room `office:{companyId}` |
| GET/PUT | `/companies/{id}/office-layout` | desk/location map |

## WebSocket events (Redis `atrium:events:{companyId}` → Colyseus room broadcast)

```
agent.status_changed  {agentId, status, activity?, taskId?}
agent.bubble          {agentId, icon: 'email'|'code'|'chart'|'chat', text?}
task.created|progress|flagged|completed|approved|rejected  {taskId, …}
chat.message          {channelId, sender, text}
announcement.created  {id, title, category}
bot.notice            {text}            # Atrium Bot toasts
```

Client→server (Colyseus messages, user avatar only): `move {x,y,anim}`, `sit {locationKey}`, `chat {text}` (proximity bubble; also POSTed to #general via core-api).

## Contract rules
1. Any endpoint change updates this file **first**, then code, then `shared-types/`.
2. Workers authenticate as their agent (dev: `X-Agent-Id`; Phase 3: service token per company).
3. All list endpoints paginate: `?limit=&cursor=` with `nextCursor` in the response envelope `{data, nextCursor?}`.
4. Progress/claim/complete endpoints are idempotent by `(taskId, expected current status)` — wrong-state calls return 409.
