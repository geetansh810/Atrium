# routing

**What:** [[core-api]] module owning tasks, subtasks, skill queues, claim/lease, and the task state machine (`queued → claimed → in_progress → pending_review → approved|rejected`, + `flagged`, `cancelled`).

**State: NOT STARTED.** Milestones M0.3 (create/list/get/events), M0.4 (claim loop & lease), M0.6 (approve/reject), M2.2 (dependencies + flow graph) — M0.3/M0.4 cards amended in `atrium-docs/17-backend-execution-plan.md`.

**Rev C additions ([[agent-platform]]):** claim query gains `attempt = attempt + 1` + paused-agent guard (17 §M0.4 supersedes 03's SQL); every state change also writes `outbox_events` in-tx via `TaskEventRecorder`; tasks carry `billing_task_id`/`request_depth`; M2.2 fan-out is exact-once via `task_decompositions` fingerprint; claim 409 = never retry.

**Key rules:**
- Claim = canonical `FOR UPDATE SKIP LOCKED` query from [[data-model]] **verbatim**, + 10-min lease + `BudgetGuard.canSpend` ([[accountability]]) in ONE transaction. `LeaseReclaimJob` (@Scheduled 60s) requeues expired leases.
- Every transition appends `task_events` in the same tx (`TaskStateGuard`, wrong status → 409).
- Skill validated against roster via registry's `AgentDirectory` interface.
- Approve blocked while children/subtasks open. `if (skill == …)` here = bug by definition.

**Contracts:** [[data-model]] (tasks/subtasks/task_events + claim query) · `04-api-contract.md §Tasks` · `05-module-specs.md §routing`.

Links: [[_Atrium]] · [[core-api]] · [[registry]] · [[execution]] · [[accountability]] · [[data-model]]
