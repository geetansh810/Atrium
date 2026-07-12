# agent-platform

**What:** the Rev-C backend design making three axes pluggable without schema breaks: any LLM (provider SPI + `model_catalog`), any agent type (`AgentRuntime` SPI, `agents.runtime_type/runtime_config`), any depth (skills + memories + knowledge + learning pipeline in the new `agentmind` module, plus the `eventbus` outbox backbone).

**State: OUTBOX WRITER LIVE** (2026-07-13, M0.3). `eventbus/` DomainEvent + Topics + OutboxEvent entity + OutboxWriter (`MANDATORY` propagation — same-tx is structural). Relay/cursors = M0.75; SPIs/agentmind still spec-only.

**The docs (load per task):**
- `atrium-docs/12-backend-architecture.md` — modules, outbox/topic backbone, gateways, scalability path. Wins over 02 on conflict.
- `13-llm-and-agent-spi.md` — normative Java SPIs + error taxonomy + model catalog.
- `14-skills-memory-learning.md` — the differentiator: skills registry, MemoryStore SPI (pgvector v1), ContextAssembler prompt doorway, governed learning pipeline.
- `15-data-model-delta.md` — new tables + **migration renumbering** (V1 core, V2 agent platform, V3 comms/office, V4 tenancy).
- `16-api-contract-delta.md` — skills/memory/review-queue/catalog endpoints, Worker API gateway.
- `17-backend-execution-plan.md` — **the build script**: MB-0 → M0.x (amended) → M-SK1/CTX1/MEM1/LN1/KN1/AR1/LN2 cards with Done-whens.
- `notes/paperclip-findings.md` + `notes/solace-agent-mesh-findings.md` — pattern provenance (M0.0 Paperclip half ✅).

**Hard rules added:** outbox row in same tx as every state change · only active/trusted content reaches ContextAssembler · learning writes governed (agent-scope auto, role/company scope human-reviewed) · idempotency namespaces `taskId:attempt` / `learn:` / `embed:` · singleton jobs take advisory locks.

Links: [[_Atrium]] · [[core-api]] · [[execution]] · [[data-model]] · [[milestones]] · [[realtimebridge]]
