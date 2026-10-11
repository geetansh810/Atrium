# 10 · FAQ

## Why is my task stuck in queued?

Agents pull work; nothing pushes it. A queued task moves only when an agent that (a) has the task's `requiredSkill` in its skill tags, (b) isn't paused, (c) isn't over budget, and (d) has a **configured provider key** ticks (every ~15s). The usual cause is (d): the agent's provider has no API key in the core-api container, so its loop parks. Check `docker compose logs core-api | grep parked`. Mission Control shows a banner after 2 minutes of this. Fix: set the key in the root `.env` and `docker compose up -d --build core-api`.

## I restarted core-api and my agents never claim anything.

A container rebuild loses the in-memory poll threads, and they are started on hire/unpause. Pause then unpause an agent (or re-PATCH `paused`) to restart its loop. This is a known gap: there is no start-all-on-boot.

## Why did a rejected task come back with different work?

Reject requeues the task and clears the assignment, so *any* eligible agent may claim it. The reviewer's feedback is read from the latest `rejected` event and added to that attempt's prompt.

## What is the difference between `usage_records`, `llm_request_logs` and `task_events`?

`usage_records` is the **billing ledger** (one row per successful, non-deduplicated call). `llm_request_logs` is the **debugging trace** (full prompt/response, includes failures, purged). `task_events` is the **permanent audit trail** (state changes). Different retention, different purpose — see [06](06-data-model.md).

## Can an agent approve its own work?

No. Approval is a human API action behind the `pending_review` gate. Roles with `review_required` are additionally refused for any non-human actor.

## Can two agents claim the same task?

No. The claim is a single `UPDATE … FOR UPDATE SKIP LOCKED`; exactly one racer wins and the rest get a 409. A redelivered task after a lease expiry cannot double-bill because usage is keyed `taskId:attempt`.

## How do I add a new kind of agent (e.g. Marketing)?

Data, not code: create a role definition (`POST /role-definitions`), hire an agent against it with skill tags, optionally attach skills/knowledge to the role. No routing change. See the proof in [`07-milestones.md`](../07-milestones.md) M1.1.

## How do I add another LLM provider?

Implement [`LlmProvider`](../../core-api/src/main/java/app/atrium/execution/spi/LlmProvider.java), expose its key via config, add `model_catalog` rows in a migration, add a WireMock test. [`GoogleClient`](../../core-api/src/main/java/app/atrium/execution/GoogleClient.java) is the template. The router, loop and routing are untouched. Verify model names against the live provider with a real key — names go stale quickly.

## Why does the agent's memory recall return nothing?

Either no memory is `active` for its scope, the similarity is below 0.30, or no embeddings key is configured (memories then have NULL embeddings and are never recalled). Browse a memory's status on the employee profile's Memory tab.

## Why do I get zero rows instead of an error after adding a query?

Row Level Security. The company GUC is set when a transaction begins; a repository call outside any `@Transactional` boundary sees nothing. Add the boundary — see [08](08-security-tenancy.md).

## Why is the Projects page "Sample data"?

There is no projects backend; the page is a fixture-backed domain linked to real task ids client-side. Workflow templates are the same.

## Is it production-ready?

It is pilot-ready: tenant isolation, rate limiting, structured logs, alerting and CI exist. It is not sold: no billing, no account deletion, a seeded admin account, no token revocation, and the AWS deploy has never been applied. See [01](01-what-is-atrium.md#what-is-built-and-what-is-not) and [`compliance/eu-ai-act-posture.md`](../compliance/eu-ai-act-posture.md).

## Where do I find what is being built next?

[`07-milestones.md`](../07-milestones.md) (Phase 5 — Product Experience: UX1–UX5, OPS1, KB1) and the "Current state / Likely next" section of [`CLAUDE.md`](../../CLAUDE.md).
