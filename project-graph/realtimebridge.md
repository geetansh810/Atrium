# realtimebridge

**What:** thin [[core-api]] module that publishes state-change events to Redis pub/sub AFTER_COMMIT — the one-way bridge to [[office-realtime]]. Events out, nothing in.

**State: NOT STARTED.** Milestone M2.4c.

**Key rules:** publish only after the business tx commits; channel `atrium:events:{companyId}` (always company-prefixed); event payloads per [[realtime-events]]. This module never handles requests and owns no tables.

**Rev C:** implementation changed from `@TransactionalEventListener(AFTER_COMMIT)` to the **outbox relay** — business code writes `outbox_events` in-tx (eventbus module), `OutboxRelay` (250ms poll, SKIP LOCKED, advisory lock) publishes to Redis. Same channel contract, now at-least-once and shared with durable consumers. Built at **M0.75** (earlier than M2.4c). See [[agent-platform]] / `atrium-docs/12-backend-architecture.md` §3–4.

**Contracts:** `atrium-docs/02-architecture.md §4` · `04-api-contract.md` (WS events section).

Links: [[_Atrium]] · [[core-api]] · [[realtime-events]] · [[office-realtime]]
