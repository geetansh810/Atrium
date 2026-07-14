# agent-platform

**What:** the Rev-C backend design making three axes pluggable without schema breaks: any LLM (provider SPI + `model_catalog`), any agent type (`AgentRuntime` SPI, `agents.runtime_type/runtime_config`), any depth (skills + memories + knowledge + learning pipeline in the new `agentmind` module, plus the `eventbus` outbox backbone).

**State: FULL FOUNDATION LOOP + EVENT RELAY LIVE + SKILLS REGISTRY LIVE** (2026-07-14, M0.3–M0.75 + M-SK1). `eventbus/` DomainEvent + Topics (incl. `Topics.budget`, M0.7) + OutboxEvent entity + OutboxWriter (`MANDATORY` propagation — same-tx is structural) + `OutboxRelayGateway`/`JpaOutboxRelayGateway` (SKIP LOCKED claim + publish-stamp, same tx) + `EventCursorWorker` (durable-consumer base, no concrete consumer yet) + `OutboxRetentionJob` (nightly, 14-day/cursor-floor purge) — all M0.75. 13 §1 LLM provider SPI is built (`LlmProvider`/`LlmClient`/`LlmRouter`/`AnthropicClient`, M0.5a) and 13 §3 AgentRuntime SPI has its first real implementation (`LlmLoopRuntime`, M0.5b — replaces the M0.2 placeholder), now feedback-aware on rework (M0.6). The full task lifecycle is real end to end: create → claim → work → pending_review → approve/reject → (requeue) → budget-gated claim → auto-pause/resume (M0.6, M0.7) → **relayed to Redis for office/dashboard within ~250ms** (M0.75, [[realtimebridge]]). `agentmind` (14 §1: skills registry) is now LIVE — see [[agentmind]] for the module. Still spec-only within agentmind: memory (M-MEM1), knowledge (M-KN1), learning pipeline (M-LN1).

**The docs (load per task):**
- `atrium-docs/12-backend-architecture.md` — modules, outbox/topic backbone, gateways, scalability path. Wins over 02 on conflict.
- `13-llm-and-agent-spi.md` — normative Java SPIs + error taxonomy + model catalog.
- `14-skills-memory-learning.md` — the differentiator: skills registry, MemoryStore SPI (pgvector v1), ContextAssembler prompt doorway, governed learning pipeline.
- `15-data-model-delta.md` — new tables + **migration renumbering** (V1 core, V2 agent platform, V3 comms/office, V4 tenancy).
- `16-api-contract-delta.md` — skills/memory/review-queue/catalog endpoints, Worker API gateway.
- `17-backend-execution-plan.md` — **the build script**: MB-0 → M0.x (amended) → M-SK1/CTX1/MEM1/LN1/KN1/AR1/LN2 cards with Done-whens.
- `notes/paperclip-findings.md` + `notes/solace-agent-mesh-findings.md` — pattern provenance (M0.0 Paperclip half ✅).

**Hard rules added:** outbox row in same tx as every state change · only active/trusted content reaches ContextAssembler · learning writes governed (agent-scope auto, role/company scope human-reviewed) · idempotency namespaces `taskId:attempt` / `learn:` / `embed:` · singleton jobs take advisory locks.

Links: [[_Atrium]] · [[core-api]] · [[execution]] · [[agentmind]] · [[data-model]] · [[milestones]] · [[realtimebridge]]
