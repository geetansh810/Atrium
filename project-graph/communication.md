# communication

**What:** [[core-api]] module owning chat channels, messages, announcements, Atrium Bot notices, and the pipeline that turns agent work into chat activity. New at M2.5.

**State: LIVE (M2.5, 2026-07-16 session 24).** New `V7__communication.sql` (03 §V2 channels/messages/announcements — `office_layout` dropped since the office track retired 2026-07-15). `Channel`/`Message`/`Announcement` JPA entities + repos (tenant-scoped: messages are reached only through a channel already verified to belong to the company). `ChannelService` — list/create channels, keyset-paginated `?before=` message history (newest-first), `postMessage` (emits `chat.message` outbox in-tx via [[realtimebridge]]'s eventbus), `ensureChannel` (get-or-create by the `(company,name)` unique key). `AnnouncementService` — list/create, emits `announcement.created`. `ChannelController`/`AnnouncementController` per 04 §Communication. New `Topics.chat(companyId, channelId)`.

**`ChatNoticePipeline extends eventbus.EventCursorWorker`** — the THIRD durable-consumer subclass (after [[agentmind]]'s `LearningPipeline` and [[accountability]]'s `StatsRollupWorker`; same `@Lazy`-self + `@Scheduled poll()` idiom). Consumes `task.completed`, resolves the agent display name via [[registry]]'s `AgentDirectory`, posts "🤖 {agent} finished '{title}' — ready for review." to `#general` (auto-created via `ensureChannel`). Reads ONLY the payload's `agentId`+`title` — never a `Task`/`TaskRepository` (communication has no dependency on [[routing]]) — which is why `routing.TaskService.complete()` now also puts `title` on the `task.completed` payload (16 §4, additive; the guard skips events missing it, so pre-M2.5 historical completions never backfill). Idempotency is lossy-OK by design (a chat log, not billed state), documented in the class — same posture [[realtimebridge]] takes.

**Escalations:** the `GET /companies/{id}/escalations` endpoint (flagged + pending_review, newest first) lives in [[routing]] (`TaskService.listEscalations`), not here — it's a task query. The frontend `ReviewInbox` page ([[web-dashboard]]) is the ≤2-click surface (client-side filter, works in both mock/API modes).

**Boundary (12 §2):** `communication → registry` (AgentDirectory, agent names) + durable eventbus consumer (reads outbox via `EventCursorWorker`, writes via `OutboxWriter`). Never imports routing/execution internals.

**Done-when verified live** against the real Agrawal Namkeen tenant (not just the 8 new tests): Priya completed a real task → the bot notice appeared in `#general` within one 5s tick → rendered in the actual ChatPanel; a user message sent through the real composer round-tripped (sender "user"). See [[milestones]] for the full story.

**Contracts:** [[data-model]] (channels, messages, announcements) · `04-api-contract.md §Communication` · `16-api-contract-delta.md §4` (chat.message/announcement.created/title payload).

Links: [[_Atrium]] · [[core-api]] · [[registry]] · [[routing]] · [[realtimebridge]] · [[data-model]] · [[web-dashboard]]
