# 5 · Life of a task

The one flow that touches every module. Read this and you can explain the platform. Deeper treatment: [`18-technical-architecture.md`](../18-technical-architecture.md) §7–9.

## State machine

Only [`TaskStateGuard`](../../core-api/src/main/java/app/atrium/routing/domain/TaskStateGuard.java) may change `tasks.status`; an illegal move is a `409`.

```
queued ──claim──► claimed ──progress──► in_progress ──complete──► pending_review ──approve──► approved ■
  ▲                 │ ▲                     │                        │
  │   lease expiry  │ │                     └──► flagged ◄───────────┤ (also from claimed)
  └─────requeue─────┘ └── flagged → in_progress | queued           reject
  ▲                                                                   │
  └──────────────── rejected → queued (same transaction) ◄───────────┘
cancelled ■ is legal from any non-terminal state in the guard, but no endpoint or job sets it yet
```

## The steps

| # | Step | What happens | Code |
|---|---|---|---|
| 1 | **Create** | `POST /companies/{id}/tasks`. Rejected with a field error if no agent in the company has the `requiredSkill`. A `created` event is written with actor `user:<id>`. Child tasks inherit `billing_task_id` and `request_depth`. | [`TaskService.create`](../../core-api/src/main/java/app/atrium/routing/TaskService.java) |
| 2 | **Wait** | The task sits `queued`. Nothing assigns it — agents *pull* work; the router never pushes. | |
| 3 | **Poll** | Each active agent has one virtual thread that ticks every 15s (fixed; `pollSeconds` is accepted in config but not read). Step 0: if the agent's provider has no key, park silently and claim nothing. | [`LlmLoopRuntime.runOnceInternal`](../../core-api/src/main/java/app/atrium/execution/LlmLoopRuntime.java) |
| 4 | **Gate** | Paused agents and agents over budget (own cap or company-wide) get no task. A breach auto-pauses an agent that hit its own cap. | [`BudgetService.canSpend`](../../core-api/src/main/java/app/atrium/accountability/BudgetService.java) |
| 5 | **Claim** | One SQL `UPDATE … WHERE id = (SELECT … FOR UPDATE SKIP LOCKED LIMIT 1)` picks the highest-priority, oldest task whose skill the agent has; sets a 10-minute lease and `attempt = attempt + 1`. Under N racing workers exactly one wins. A `claimed` event is written in the same transaction. | [`PostgresWorkBroker.CLAIM_NEXT_SQL`](../../core-api/src/main/java/app/atrium/routing/PostgresWorkBroker.java), canonical form in [`03-data-model.md`](../03-data-model.md) |
| 6 | **Assemble context** | *Inside* the claim transaction, skills + memories + knowledge are selected under a token budget and their ids are stored in the `claimed` event as `contextProvenance`. | [`SkillContextAssembler`](../../core-api/src/main/java/app/atrium/agentmind/SkillContextAssembler.java) |
| 7 | **Start** | `claimed → in_progress` (`progress` event). If `attempt` exceeds `maxAttemptsPerTask` (default 3) the task is flagged `max_attempts` before any spend. | `TaskService.progress`, `flag` |
| 8 | **Prompt** | Built from *only* the role definition, task, reviewer feedback (if previously rejected) and the context bundle — never secrets. | [`PromptAssembler`](../../core-api/src/main/java/app/atrium/execution/PromptAssembler.java) |
| 9 | **LLM call** | Through [`LlmRouter`](../../core-api/src/main/java/app/atrium/execution/LlmRouter.java): catalog validation, 120s timeout, taxonomy-driven retries (rate-limited 2s/8s/30s, provider-down 5s/20s) — the only place retries live. Every call (success or final error) is written to `llm_request_logs`. | [`AnthropicClient`](../../core-api/src/main/java/app/atrium/execution/AnthropicClient.java), [`GoogleClient`](../../core-api/src/main/java/app/atrium/execution/GoogleClient.java) |
| 10 | **Record + complete** | One transaction: insert the `usage_records` row (idempotency key `taskId:attempt`, `ON CONFLICT DO NOTHING`), add its tokens to the budget, store the artifact, move to `pending_review`, write the `completed` event with the model, prompt version and inputs. If the model called `create_child_tasks`, the same transaction fans out child tasks exactly once. | [`UsageRecorder`](../../core-api/src/main/java/app/atrium/execution/UsageRecorder.java), `TaskService.complete`/`completeWithDecomposition` |
| 11 | **Review** | A human sees it in Tasks / Review Inbox. **Approve** is blocked while child tasks or subtasks are open, and — for `review_required` roles (`legal`, `hr`) — unless a named human is the actor (`403` otherwise). **Reject** needs feedback. | `TaskService.approve`/`reject` |
| 12 | **Rework** | Reject writes `rejected` then `requeued` in one transaction and clears the assignment. The next claim gets a new `attempt` (so the retry is billed under its own key) and the feedback rides into the prompt as "Reviewer feedback (attempt N)". | |
| 13 | **Afterwards** | Durable consumers read the outbox: the learning pipeline extracts memories from the rejection/approval, the stats worker updates `agent_stats_daily`, the chat pipeline posts a notice to `#general`. | see [07](07-skills-memory-learning.md) |

## Failure paths

| What goes wrong | What the system does |
|---|---|
| Agent dies mid-work (lease expires) | `LeaseReclaimJob` (every 60s, advisory-locked singleton) requeues it with a `requeued` event; the next claim bumps `attempt`. A late duplicate usage insert is a silent no-op. |
| Provider 429 / 5xx | In-attempt retries, then flag `provider_rate_limited` / `provider_unavailable`. |
| Bad or missing key / unknown model | No retry; flag `config_incomplete`. With no key at all the agent never claims (step 3). |
| Context too long / refusal | No retry; flag `context_too_long` / `content_filtered`. |
| Our bug (bad request) | Flag `execution_error`, logged at ERROR (which can fire the alert webhook). |
| Agent over budget | Claim refused; own-cap breach pauses the agent and emits `budget.exceeded`; raise the cap and unpause to resume. |

Flagged tasks appear in the Review Inbox. **Known gap:** the guard allows `flagged → in_progress | queued | cancelled`, but no endpoint or job currently performs those moves — a flagged task is resolved today by fixing the cause (e.g. adding the API key) and an operator moving it, not from the UI. Separately, the Mission Control banner covers a different case: *queued* tasks nobody claims for 2+ minutes.

## Reading a task's history

`GET /tasks/{id}/events` returns the append-only audit trail. Event types in order of a clean run: `created`, `claimed`, `progress`, `completed`, `approved`. The `claimed` payload says *what the agent was given*; `completed` says *which model, which prompt version, which inputs*; combined they reconstruct the run from `task_events` alone — see [`compliance/review-process.md`](../compliance/review-process.md).

## Follow it live

```bash
# after `docker compose up` and a signup (see 09)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/tasks/$TASK_ID/events | jq '.data[] | {eventType, actor, createdAt}'
```

*Verified against:* `TaskService`, `PostgresWorkBroker`, `LlmLoopRuntime`, `LlmRouter`, `FlagReasons`, `LeaseReclaimJob`; behavior pinned by [`ClaimApiTest`](../../core-api/src/test/java/app/atrium/routing/ClaimApiTest.java), [`TaskApprovalGateTest`](../../core-api/src/test/java/app/atrium/routing/TaskApprovalGateTest.java), [`LlmLoopRuntimeTest`](../../core-api/src/test/java/app/atrium/execution/LlmLoopRuntimeTest.java), [`ComplianceGateTest`](../../core-api/src/test/java/app/atrium/routing/ComplianceGateTest.java).
