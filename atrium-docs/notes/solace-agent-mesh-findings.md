# Solace Agent Mesh Findings

Researched 2026-07-12 from `github.com/SolaceLabs/solace-agent-mesh` + official docs (`solacelabs.github.io/solace-agent-mesh`). Python/ADK framework over Solace event brokers using the A2A protocol. We mine its **event-driven shape**, not its stack.

## Their architecture (verified from docs)

- **Broker as nervous system**: all component communication is async over event topics; components never call each other directly.
- **Topic taxonomy** (A2A over hierarchical topics):
  - discovery: `{namespace}/a2a/v1/discovery/agentcards`
  - request: `{namespace}/a2a/v1/agent/request/{target_agent_name}`
  - gateway status/response: `{namespace}/a2a/v1/gateway/{status|response}/{gateway_id}/{task_id}`
  - peer status/response: `{namespace}/a2a/v1/agent/{status|response}/{delegating_agent}/{sub_task_id}`
- **AgentCards**: each agent host publishes a JSON capability card to the discovery topic at startup + periodically; subscribers maintain a local AgentRegistry. New agents join with zero config changes elsewhere.
- **Gateways**: protocol translators (HTTP/WebSocket/Slack ↔ A2A) that also own authn, permission-scope retrieval, external-session ↔ task-lifecycle mapping, and late-stage artifact resolution.
- **Orchestrator is an agent**, not infrastructure: breaks requests into subtasks, tracks dependencies, runs parallel branches, aggregates results. Multiple orchestrators per domain possible.
- **Delegation propagates the original user's permission scopes** so downstream agents enforce the originating security context.
- **Streaming**: agent execution emits status + artifact update events to the originator's status topic; final response on the response topic.
- **Stateless agent hosts** scale horizontally; state lives in broker + stores.

## What Atrium adopts (sized for v1 — no external broker yet, per 02 §8)

1. **Topics as the canonical event address**, even while the transport is Postgres outbox + Redis. Every domain event gets a `topic` string following an Atrium taxonomy (12 §4). Swapping Redis→Solace/Kafka later = new relay, zero producer changes.
2. **Transactional outbox as our "broker"**: SAM gets reliability from the broker; we get it from an `outbox_events` table written in the business transaction + a relay. Consumers keep durable cursors — a poor-man's event mesh that is upgrade-compatible.
3. **AgentCard ≈ roster row**: `agents` + `role_definitions` + `skill_tags` already form the capability registry; `AgentDirectory` is the AgentRegistry. We add nothing — we just note the equivalence so a future external-agent runtime can publish real cards.
4. **Gateway pattern named explicitly**: the REST API is the *human gateway*; future Slack/email/webhook gateways are new modules that translate their protocol into the same commands (create task, message, approve) and consume the same event stream. Contract: gateways depend only on 04-contract endpoints + the event taxonomy — never on module internals.
5. **Orchestrator-as-agent**: manager/PM agents (M2.2) decompose parent tasks into child tasks through the normal task API — orchestration is data + a role, not router code. This is exactly our "roles are data" rule; SAM validates it at scale.
6. **Scope propagation**: child tasks inherit `company_id` + `created_by` chain and a billing attribution to the parent (Paperclip billing codes) — the Atrium analog of SAM's permission-scope propagation.
7. **Status/artifact streaming shape**: our `task.progress` / `task.completed` events on `atrium:events:{companyId}` mirror SAM's status/response topics; the office and dashboard are just subscribers.

## What we deliberately do NOT adopt (v1)

- No external broker (Solace/Kafka/Rabbit) — 02 §8 non-goal stands; the outbox + `WorkBroker`/`EventPublisher` interfaces are the contained swap points.
- No dynamic runtime agent discovery — roster is authoritative in Postgres; "discovery" is a query.
- No A2A wire protocol — internal Java interfaces; the taxonomy keeps us protocol-compatible in spirit.
- No per-message permission tokens — tenant scoping via TenantContext + (Phase 3) RLS covers our threat model.
