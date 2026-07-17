# 16 — API Contract Delta (agent platform)

Additive companion to `04-api-contract.md` — all rules there apply (problem+json, dev headers, pagination envelope `{data, nextCursor?}`, contract-first). Frontend `shared/types.ts` mirrors these when the endpoints are built (17 says which milestone).

## 1. Registry additions

| Method | Path | Notes |
|---|---|---|
| GET | `/model-catalog` | enabled models: provider, modelName, displayName, tier, capabilities, contextWindow (prices are admin-only — omit in v1 response) |
| POST | `/companies/{id}/agents` | body gains optional `runtimeType` (default `llm_loop`), `runtimeConfig` (validated per 13 §3.1 → 400 with field errors), `skillIds[]` (extra attaches beyond template set) |
| PATCH | `/agents/{id}` | same new fields patchable; `paused` boolean (board pause — stops runtime, refuses claims) |
| GET | `/agents/{id}/mind` | the depth view: `{skills:[{id,key,name,kind,source,proficiency}], memoryCounts:{byScope,byStatus}, knowledgeDocs:[…]}` — feeds Agent Profile "Skills" + new "Learning" section |

## 2. Skills

| Method | Path | Notes |
|---|---|---|
| GET | `/companies/{id}/skills?kind=&tag=` | company + global (company_id NULL) skills, latest version each |
| POST | `/companies/{id}/skills` | `{key, name, description, bodyMd, kind, tags[]}` → Skill (trust_level=company) |
| POST | `/skills/{id}/versions` | new version of an existing key → Skill |
| GET | `/skills/{id}` | full body |
| POST | `/agents/{agentId}/skills` | `{skillId, proficiency?}` attach · DELETE `/agents/{agentId}/skills/{skillId}` detach |
| POST | `/role-definitions/{id}/skills` | `{skillId, position?}` attach to role (affects future hires; existing agents keep their own set) |

## 3. Memory & knowledge

| Method | Path | Notes |
|---|---|---|
| GET | `/companies/{id}/memories?scope=&agentId=&kind=&status=&q=` | browse/inspect; `q` = semantic search; rows include provenance |
| POST | `/companies/{id}/memories` | seed manually: `{scope, agentId?, roleKey?, kind, content}` → active immediately (human-authored) |
| DELETE | `/memories/{id}` | forget (archives — audit-safe) |
| GET | `/companies/{id}/memories/review-queue` | `pending_review` items, oldest first, with provenance + source task link |
| POST | `/memories/{id}/review` | `{action:'approve'|'reject', promoteScope?:'role'|'company', asSkill?:{key,name}}` — approve/reject; optionally promote scope or convert to draft skill (14 §5) |
| POST | `/companies/{id}/knowledge` | `{title, content}` or multipart text/markdown upload → doc (status=ingesting→active) |
| GET | `/companies/{id}/knowledge` · DELETE `/knowledge/{id}` | list / archive |
| POST | `/role-definitions/{id}/knowledge` | `{docId}` attach doc to role |

Review-queue actions append `task_events`-style audit rows? No — memories are not tasks; audit = `memory.review_requested` / `memory.promoted` events on the outbox (12 §4) + `provenance.reviewedBy`. Escalations page (M2.5) adds a third tab "Learning review" reading the review queue.

## 4. New event payloads (extends 04 §WebSocket / topic taxonomy 12 §4)

**M-LN1 addition:** `task.completed`/`task.rejected`/`task.approved` payloads (04, unchanged shape otherwise) now also carry `agentId` — the agent who did the work being reviewed. Additive only (no existing field renamed/removed). Needed because `LearningPipeline` (a durable outbox consumer, 12 §3) processes these asynchronously, by which point `tasks.assigned_agent_id` may already be cleared (e.g. `reject()` clears it in the same transaction that writes the `rejected` event) — the payload is the only reliable place left to find "whose work is this."

**M2.5 addition:** `task.completed` also carries `title` (the task's title). Additive only. Same asynchronous-consumer reason as `agentId`: `communication.ChatNoticePipeline` turns each `task.completed` into a bot message in `#general` ("🤖 {agent} finished '{title}' — ready for review") and needs the title without a cross-module read back into `routing`. `chat.message {channelId, sender, text}` and `announcement.created {id, title, category}` (04 §WS) are now actually produced — by `communication` via `OutboxWriter` on every message/announcement write.

**M4.2 addition (07 Phase 4, compliance):** `task.completed`'s underlying `task_events` row (and its outbox mirror) gains, whenever the completion came through `LlmLoopRuntime` (never `EchoRuntime`, which has nothing real to report): `model` (`"<provider>/<modelName>"`), `roleDefinitionId`, `promptVersion` (the assigned agent's role_definition `version` at completion time — role rows are immutable per version, so this address is enough to look the exact system_prompt/output_contract back up), and `inputs:{title, description?, feedback?}` — the literal task fields `PromptAssembler.build` fed into the prompt for this attempt. Combined with the `claimed` event's pre-existing `contextProvenance` (M-CTX1: which skill/memory/knowledge rows were recalled), this makes every completion **fully reconstructable from `task_events` alone** — the actual mechanism behind the "full reconstructable audit on any HR task" Done-when (07 M4.2), applied uniformly to every role rather than gated on a role key (roles are data — no `if (roleKey=='hr')` branch). All fields additive; existing readers (`ChatNoticePipeline`, `LearningPipeline`) are unaffected.

```
budget.threshold          {agentId?, period, spentTokens, capTokens, pct}     # once per period at alert_pct
budget.exceeded           {agentId?, period}                                  # claim refused + auto-pause
memory.learned            {agentId, memoryId, kind, scope}                    # auto-activated lesson/summary
memory.review_requested   {memoryId, scope, kind, sourceTaskId}
memory.promoted           {memoryId, fromScope, toScope}
agent.hired|agent.updated {agentId}
```

## 5. Worker API clarification (the agent gateway, 12 §5)

Unchanged endpoints, now explicitly the contract external runtimes (webhook/process, 13 §3.3) must use: `POST /tasks/{id}/claim | lease/renew | progress | complete | flag` with `X-Agent-Id` (Phase 3: per-company service token). `claim` responses include the ContextBundle when `runtime_config.contextMode='fat'`. 409 semantics per 04 rule 4; workers never retry a 409 claim.
