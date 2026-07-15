# realtimebridge

**What:** thin [[core-api]] module that publishes state-change events to Redis pub/sub AFTER_COMMIT — a one-way bridge to any realtime subscriber. Events out, nothing in. (Its planned first subscriber, [[office-realtime]], was retired 2026-07-15 — the relay stays live and generic, currently subscriber-less; the dashboard polls HTTP instead.)

**State: LIVE (M0.75, 2026-07-13).** `OutboxRelay` (`@Scheduled` 250ms, `pg_try_advisory_xact_lock` singleton): one transaction claims a `FOR UPDATE SKIP LOCKED` batch via eventbus's `OutboxRelayGateway`, publishes each to Redis `atrium:events:{companyId}` (`{type, ...payload, ts}`), stamps `published_at` — crash between claim and commit leaves rows unpublished for the next tick, so it's at-least-once by construction, not by retry logic. Micrometer `atrium.relay.lag` timer + `atrium.relay.pending` gauge.

**Key rules:** publish only after the business tx commits; channel `atrium:events:{companyId}` (always company-prefixed); event payloads per [[realtime-events]]. This module never handles requests and owns no tables (the claim/mark SQL lives in eventbus's own `OutboxRelayGateway` — realtimebridge only calls it + Redis).

**Rev C:** implementation is the **outbox relay**, not `@TransactionalEventListener(AFTER_COMMIT)` — business code writes `outbox_events` in-tx (eventbus module), `OutboxRelay` relays it. Same channel contract, at-least-once, shared with durable consumers (`EventCursorWorker`, eventbus — no concrete consumer yet, M-LN1 is the first). See [[agent-platform]] / `atrium-docs/12-backend-architecture.md` §3–4.

**Contracts:** `atrium-docs/02-architecture.md §4` · `04-api-contract.md` (WS events section).

Links: [[_Atrium]] · [[core-api]] · [[realtime-events]]
