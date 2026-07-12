# 04 — API Contract

> **Rev C:** `16-api-contract-delta.md` adds skills/memory/review-queue/model-catalog endpoints, new agent fields (`runtimeType`, `runtimeConfig`, `paused`), new event payloads, and names the claim/progress/complete set below as the **Worker API gateway** for external agent runtimes. Read 16 alongside this file.

Base URL: `/api/v1`. JSON everywhere. Errors: RFC-7807 `application/problem+json`. Auth: Phase 0–2 dev header `X-Company-Id` + `X-User-Id`; Phase 3 replaces with `Authorization: Bearer <JWT>` (claims: userId, companyId, role) — endpoint shapes do not change.

## Registry

| Method | Path | Body → Response |
|---|---|---|
| POST | `/companies` | `{name, slug}` → Company |
| GET | `/companies/{id}` | → Company |
| POST | `/companies/{id}/agents` | `{name, spriteKey, roleDefinitionId?, roleTemplateKey?, roleTitle, skillTags[], modelProvider, modelName, managerAgentId?, about?}` → Agent |
| GET | `/companies/{id}/roster` | → `Agent[]` (with live status, current activity line) |
| PATCH | `/agents/{id}` | partial update → Agent |
| GET | `/agents/{id}/profile` | → `{agent, stats:{tasksCompleted, successRate, focusMinutes}, skills[], currentTasks[], activityFeed[]}` |
| GET/POST | `/role-definitions` | list global+company templates / create custom |

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
| POST | `/tasks/{id}/approve` | human/supervisor; blocked if open children/subtasks |
| POST | `/tasks/{id}/reject` | `{feedback}` → back to in_progress |
| GET | `/tasks/{id}/events` | full audit trail |
| GET | `/tasks/{id}/flow` | parent/children graph for the Task Flow view |
| GET | `/companies/{id}/escalations` | all flagged/pending_review, newest first |

## Accountability

| Method | Path | Notes |
|---|---|---|
| GET | `/companies/{id}/budget?period=YYYY-MM` | caps + spend, company & per-agent |
| PUT | `/companies/{id}/budget` | `{agentId?, period, capTokens}` |
| GET | `/companies/{id}/analytics/summary` | KPI cards |
| GET | `/companies/{id}/analytics/tasks-7d` | bar-chart series |
| GET | `/companies/{id}/analytics/agent-performance` | leaderboard |
| GET | `/companies/{id}/analytics/top-skills` | skill-share chips |

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
