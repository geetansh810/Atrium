# realtime-events

**What:** the Redis pub/sub contract between [[realtimebridge]] (publisher) and [[office-realtime]] (subscriber). Channel: `atrium:events:{companyId}`.

**State: NOT STARTED** (spec only).

**Event shapes (compact JSON, dot.case types):**
```json
{ "type": "agent.status_changed", "agentId": "…", "status": "working", "activity": "…", "taskId": "…", "ts": "…" }
{ "type": "task.created" } · { "type": "task.progress", "progress": 65, "eta_minutes": 20 }
{ "type": "task.flagged", "reason": "…" } · { "type": "task.completed" } · { "type": "task.approved" }
{ "type": "chat.message", "channel": "#general", "from": "agent:…", "text": "…" }
```

**Key rules:** publish AFTER_COMMIT only · channels always company-prefixed (tenant isolation) · office consumes events but business truth stays in [[core-api]] — office state must always be rebuildable from `GET /office-state` alone.

**Contracts:** `atrium-docs/02-architecture.md §4` · `04-api-contract.md` (WS events).

Links: [[_Atrium]] · [[realtimebridge]] · [[office-realtime]] · [[web-office]]
