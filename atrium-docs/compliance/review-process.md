# Review process explainer

**Status:** internal engineering explainer, written 2026-07-17 (M4.3, `07-milestones.md` Phase 4). Describes the review/approval mechanism as actually built and tested — every mechanism named below is real, committed code, cross-referenced to the file/class that implements it.

## The baseline: every agent's work is reviewed before it ships

No agent output reaches a "shipped" state on its own. This is a hard rule stated in `CLAUDE.md` ("nothing ships unreviewed: `pending_review` is a hard gate") and enforced structurally, not by convention:

1. A task's `status` column is only ever changed through `routing.domain.TaskStateGuard` — the sole owner of the (package-private) status setter. There is exactly one legal path from work being done to work being usable: `queued → claimed → in_progress → pending_review`, and `pending_review` only ever moves to `approved` or `rejected`.
2. When an agent finishes work, `TaskService.complete()` writes the result as an `artifact` and moves the task to `pending_review` — never further. There is no code path where an agent's own completion call can mark a task `approved`.
3. `TaskService.approve()` is the only thing that can move a task to `approved`, and it is only ever called from `POST /tasks/{id}/approve` (a human-facing endpoint) or directly by a test. Approval is additionally blocked while any child task or checklist subtask is still open (`03-data-model.md` invariant 5) — a parent task can't be signed off while dependent work is still outstanding.
4. Every state transition writes an append-only `task_events` row in the same database transaction as the state change (`TaskEventRecorder`, `MANDATORY` propagation) — there is no way for a task to change status without leaving a permanent, timestamped, actor-attributed record of it.

A human reviewer's options at `pending_review` are exactly two: **approve** (ship it) or **reject** with feedback (`POST /tasks/{id}/reject {feedback}` — the task requeues, gets a fresh `attempt` number, and the feedback rides into the next attempt's prompt via `TaskService.latestRejectionFeedback`). There is no third option that skips review.

## The compliance-gated tier: some roles can never self-certify (M4.1)

The baseline above already requires *a* human-facing endpoint to approve. M4.1 adds a stronger guarantee for specific roles: a role can be flagged `review_required=true` (`role_definitions.review_required`, migration `V11__compliance_gate.sql`), and when it is, `TaskService.approve()` refuses to ship that role's work unless the approving call carries a **named human user** — `TenantContext.userId()` bound, which only happens for a real authenticated JWT request. A background job, a test's bare service call, or (hypothetically, since no such path exists) an agent trying to self-approve all resolve to no bound user, and are rejected with a 403 (`common.ForbiddenException`).

Two global role templates ship with this flag set:

- **`legal`** (Legal Associate) — every output is headed `"DRAFT — NOT LEGAL ADVICE — REQUIRES ATTORNEY REVIEW"`, cites its sources, and flags anything needing specialist review. The role has no tool access (`allowed_tools: []`) — it cannot send, file, or execute anything itself, only draft.
- **`hr`** (HR Specialist) — every output is headed `"DRAFT — HR REVIEW REQUIRED BEFORE USE"`, states which facts it relied on, and flags bias/jurisdiction risk. Same no-tool-access posture. This role is the one meant for hiring, performance, and termination-adjacent drafting work — see the EU AI Act posture note for why that specific combination matters.

This is deliberately **data, not code** — any company-owned custom role (`POST /role-definitions`) can also set `reviewRequired: true` if a company wants the same guarantee for a role of its own. Nothing in `routing` branches on a role's key; the gate reads a boolean column, the same way skill matching reads a text column. See `08-conventions.md` security rule 8 for the exact mechanism and `03-data-model.md` invariant 6.

**What this does and does not guarantee.** It guarantees that a *specific, timestamped human user account* signed off — the `approved` `task_events` row records `user:<id>`, and no other actor shape can appear there for a gated role's task. It does not guarantee the human read carefully, is qualified, or agreed with the content; that judgment call is the human's, by design — the system's job is to make the sign-off happen and be provable, not to grade it.

## The audit trail: full reconstruction from `task_events` alone (M4.2)

Every `completed` event written by the real LLM-backed runtime (`execution.LlmLoopRuntime`, not the dev-only `EchoRuntime`) carries, alongside the existing artifact/attempt fields:

- `model` — `"<provider>/<modelName>"`, e.g. `"anthropic/claude-sonnet-5"`.
- `roleDefinitionId` + `promptVersion` — `role_definitions` rows are immutable per version (a new version is a new row, never an edit — `08-conventions.md`), so this pair addresses the exact `system_prompt`/`output_contract` text used, permanently.
- `inputs: {title, description?, feedback?}` — the literal task fields `execution.PromptAssembler.build()` fed into the prompt for that attempt.

Combined with the `claimed` event's pre-existing `contextProvenance` (which skill/memory/knowledge rows were recalled into that attempt, from M-CTX1), this means an outsider — someone with nothing but an authenticated read against `GET /tasks/{id}/events`, no database access, no engineering context — can answer, for any completed task: *which model produced this, using which exact system prompt, fed which exact inputs, informed by which exact prior context.* That is the literal mechanism behind the "full reconstructable audit on any HR task" requirement — applied uniformly to every role's completions, not specially wired for `hr` (roles are data; there is no `if (roleKey == "hr")` anywhere in this payload).

See `16-api-contract-delta.md` §4 for the full payload shape and `routing.TaskService.CompletionAudit`'s javadoc for the code-level contract.

## Worked example: reviewing an HR task end to end

1. A company hires an agent against the `hr` template (`roleTemplateKey: "hr"`).
2. A task is created: *"Draft interview questions for a backend engineer role."*
3. The agent claims it, works, and completes — the task moves to `pending_review`, and its `completed` event carries the model/prompt-version/inputs audit block above.
4. `GET /companies/{id}/escalations` (or the dashboard's Review Inbox) surfaces it as awaiting review.
5. A human reads the draft — headed `"DRAFT — HR REVIEW REQUIRED BEFORE USE"`, with a Basis section and a Flags-for-reviewer section per the role's output contract.
6. The human calls `POST /tasks/{id}/approve` (or clicks Approve in the dashboard). Because `hr` is `review_required`, this only succeeds because a named, authenticated human made the call — verified in `core-api/src/test/java/app/atrium/routing/ComplianceGateTest.java`.
7. Six months later, an auditor with no engineering access pulls `GET /tasks/{id}/events` and can see, in order: who created the task, when it was claimed, which model produced the draft under which prompt version with which inputs, and which named human approved it and when.
