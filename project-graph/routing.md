# routing

**What:** [[core-api]] module owning tasks, subtasks, skill queues, claim/lease, and the task state machine (`queued → claimed → in_progress → pending_review → approved|rejected`, + `flagged`, `cancelled`).

**State: WORKER LOOP LIVE (M0.3+M0.4+M0.5b done, 2026-07-13 session 6).** M0.3: Task/Subtask/TaskEvent entities+repos (company-scoped; subtasks scoped via task join), `TaskStateGuard` (matrix in `routing/domain`, sole owner of the package-private status setter; illegal move → 409), `TaskEventRecorder` (task_events + outbox in ONE `MANDATORY`-propagation helper), create/list/get/events endpoints with keyset pagination envelope (`KeysetCursors`/`PageEnvelope` in common), skill validated via AgentDirectory, billing_task_id root=self/parent-copy + request_depth. M0.4: `WorkBroker` + `PostgresWorkBroker` (canonical claim SQL id-keyed w/ attempt++ & paused NOT EXISTS — 03 §claim updated; BudgetGuard.canSpend same tx, Noop until M0.7), `WorkerTaskController` claim/renew (X-Agent-Id; 409 names holder, never retry), `LeaseReclaimJob` (@Scheduled `atrium.lease.reclaim-ms`=60s, `pg_try_advisory_xact_lock`, SKIP LOCKED sweep, requeued event+outbox). M0.5b: `WorkBroker.claimNext(companyId, agentId, skillTags)` — skill-SET variant of the canonical query (`required_skill = ANY(?)`, `RETURNING id`, same guards); `Artifact` entity+repo; `TaskService` gains `progress()`/`complete()`/`flag()` (internal Java calls from execution's loop — no new HTTP endpoints yet, that's the Worker API gateway at M-AR1) — `progress()` does the claimed→in_progress edge the loop needs before `complete()` can reach pending_review (TaskStateGuard has no direct claimed→pending_review edge). Remaining: M0.6 (approve/reject), M2.2 (dependencies + flow graph).

**Rev C additions ([[agent-platform]]):** claim query gains `attempt = attempt + 1` + paused-agent guard (17 §M0.4 supersedes 03's SQL); every state change also writes `outbox_events` in-tx via `TaskEventRecorder`; tasks carry `billing_task_id`/`request_depth`; M2.2 fan-out is exact-once via `task_decompositions` fingerprint; claim 409 = never retry.

**Key rules:**
- Claim = canonical `FOR UPDATE SKIP LOCKED` query from [[data-model]] **verbatim**, + 10-min lease + `BudgetGuard.canSpend` ([[accountability]]) in ONE transaction. `LeaseReclaimJob` (@Scheduled 60s) requeues expired leases.
- Every transition appends `task_events` in the same tx (`TaskStateGuard`, wrong status → 409).
- Skill validated against roster via registry's `AgentDirectory` interface.
- Approve blocked while children/subtasks open. `if (skill == …)` here = bug by definition.

**Contracts:** [[data-model]] (tasks/subtasks/task_events + claim query) · `04-api-contract.md §Tasks` · `05-module-specs.md §routing`.

Links: [[_Atrium]] · [[core-api]] · [[registry]] · [[execution]] · [[accountability]] · [[data-model]]
