# realtime-events

**What:** the Redis pub/sub contract published by [[realtimebridge]]. Channel: `atrium:events:{companyId}`. (Its planned subscriber, [[office-realtime]], was retired 2026-07-15 — the contract stays live for any future realtime consumer, e.g. a dashboard WebSocket.)

**State: LIVE (M0.75).** [[realtimebridge]]'s `OutboxRelay` publishes this exact contract from the transactional outbox.

**Event shapes (compact JSON, dot.case types):**
```json
{ "type": "agent.status_changed", "agentId": "…", "status": "working", "activity": "…", "taskId": "…", "ts": "…" }
{ "type": "task.created" } · { "type": "task.progress", "progress": 65, "eta_minutes": 20 }
{ "type": "task.flagged", "reason": "…" } · { "type": "task.completed" } · { "type": "task.approved" }
{ "type": "chat.message", "channel": "#general", "from": "agent:…", "text": "…" }
```

**Key rules:** publish AFTER_COMMIT only · channels always company-prefixed (tenant isolation) · any subscriber is a pure projection — business truth stays in [[core-api]] and must always be rebuildable from its HTTP API alone.

**Contracts:** `atrium-docs/02-architecture.md §4` · `04-api-contract.md` (WS events).

Links: [[_Atrium]] · [[realtimebridge]]
