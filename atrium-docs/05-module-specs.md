# 05 — Module Specifications

Each module below is a self-contained unit an LLM session can build against. **When working in one module, an LLM should never need to modify another** — if it does, the contract (03/04) is wrong; stop and fix the contract first.

## core-api / registry
**Owns:** companies, users, agents, role_definitions, role templates.
**Provides (to other modules):** `AgentDirectory.findBySkill(companyId, skill)`, `RoleDefinitions.resolve(agentId)`.
**Must:** validate skill_tags non-empty; resolve template vs custom role on hire; maintain manager hierarchy (no cycles — validate on write).
**Must not:** know anything about tasks, queues, or LLM calls.

## core-api / routing
**Owns:** tasks, subtasks, queue semantics, claim/lease/reclaim, task flow graph, escalations.
**Provides:** the canonical claim query (03-data-model.md — copy exactly), lease renewal, requeue job (@Scheduled every 60s). The runner's `claimNext` (skill-ordered variant) also accepts a plain-JDK `Function<Task,ObjectNode>` enricher hook (M-CTX1, 14 §6) so a caller in another module can merge extra fields into the `claimed` event's payload from inside routing's own claim transaction — without routing importing that caller's types.
**Must:** company-scope every queue read; refuse approval while open children/subtasks exist; append a task_event inside the same transaction as every status change.
**Must not:** call LLMs; know provider names; contain any role-specific branching (`if skill == "coder"` is a bug by definition).

## core-api / execution
**Owns:** LlmClient abstraction + provider impls, agent runner (the worker loop), prompt assembly, artifact creation.
**Worker loop:** for each active agent → claim (context bundle assembled inside the same claim transaction via agentmind's `ContextAssembler`, M-CTX1) → build prompt (role_definition.system_prompt + skills/memories/knowledge bundle + task + feedback-if-rejected) → call LlmClient → record usage (same tx, with idempotency_key `taskId:attempt`) → update progress/subtasks → complete or flag → repeat. Poll interval configurable (default 15s); lease renewed every 5min while working.
**Must:** strip/never-include secrets in prompts; time-box calls; treat provider errors as flag-not-crash.
**Must not:** write task status directly — always through routing's service interface.

## core-api / accountability
**Owns:** budgets, usage_records, approvals, task_events read API, analytics rollups (agent_stats_daily), Atrium Bot notices.
**Must:** enforce cap at claim-time (routing calls `BudgetGuard.canSpend(companyId, agentId)`); update rollups on approve/reject; compute success_rate = approved/(approved+rejected).
**Must not:** block reads when over budget — only new claims.

## core-api / agentmind (Rev C — full spec: 14)
**Owns:** skills registry, ContextAssembler (M-CTX1 — the ONLY doorway from stored knowledge into prompts; lives here, not execution, since agentmind must never import execution types), MemoryStore SPI + pgvector impl, EmbeddingClient, LearningPipeline (durable outbox consumer), knowledge ingestion, review-queue governance.
**Provides:** `ContextAssembler.assemble(agent, task)`, consumed by execution's `LlmLoopRuntime`/`PromptAssembler` (`execution → agentmind` is the allowed dependency direction, 12 §2).
**Must:** company-scope everything; only `active` memories / `platform|company` skills reach prompts; every memory carries provenance; learning writes are governed per 14 §5.
**Must not:** write task state; call providers except via LlmClient/EmbeddingClient; be imported by routing.

## core-api / eventbus (Rev C — full spec: 12 §3)
**Owns:** `outbox_events` writer (same-tx), OutboxRelay → Redis, `event_consumers` cursors, retention.
**Must not:** contain business logic; be awaited by request paths beyond the in-tx insert.

## core-api / realtimebridge
**Owns:** publishing the event JSON (04 §WebSocket) to Redis `atrium:events:{companyId}` after commit (use `@TransactionalEventListener(AFTER_COMMIT)`).
**Must not:** be awaited by business logic; failures log-and-continue (office view is a projection, business truth is unaffected).

## office-realtime (forked SkyOffice server)
**Owns:** Colyseus `OfficeRoom` per company; user avatar presence/movement; agent avatar driving; proximity chat bubbles.
**Adapt from SkyOffice:** keep room/schema/movement/anim code; **add** (a) room-token validation against core-api on join, (b) Redis subscriber that maps events → agent avatar state (target location from office_layout, walk-to pathing can be simple straight-line v1), (c) `office-state` bootstrap fetch on room create; **strip** PeerJS/webcam/screen-share/whiteboard.
**Must not:** write to Postgres; hold state that survives restart.

## web / dashboard
**Owns:** React panels — Roster, Task Board, My Workspace (3 tabs + task detail with subtask checklist), Agent Profile, Analytics, Budget Ledger, Chat, Announcements, Escalations, Task Flow (graph), Invite Agent flow.
**Must:** consume only 04-contract endpoints via `src/shared/api.ts`; theme tokens from `theme.ts` only; check layouts against `assets/reference-*.png`.

## web / office (forked SkyOffice client)
**Owns:** Phaser 3 scene embedded at `/office`; left nav room list; Who's Here; right rail (My Tasks, Agent Status — these two consume dashboard API hooks, not Colyseus); bottom bar (Atrium Bot toast, message input).
**Adapt from SkyOffice:** keep Phaser bootstrapping, tilemap, character anims, name tags, chat bubbles; **replace** lobby/room-selection UI with auto-join `office:{companyId}`; **add** agent avatars driven by server state + status-colored name-tag dots + activity bubbles (email/code/chart icons); **strip** webcam UI.
**Asset note:** SkyOffice's bundled art is by LimeZu (itch.io) — verify the license tier for commercial hosting during M2.4a and budget for the paid pack or a replacement set if needed. Credit LimeZu and SkyOffice in an in-app credits screen either way.

## shared-types
TS interfaces mirroring 04 responses. Regenerated/hand-updated only when 04 changes. web and office-realtime import from here — never redeclare API shapes locally.
