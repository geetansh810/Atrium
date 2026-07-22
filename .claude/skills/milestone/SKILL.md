---
name: milestone
description: Run one Atrium milestone session end-to-end — load the right contracts, branch, build, verify the Done-when, update the graph, squash-merge. Use when the user says "start", "start next", "next milestone", or names a milestone id (M0.x, M-SK1, M2.4a…).
---

# Atrium milestone session

One milestone per session. The card in `atrium-docs/17-backend-execution-plan.md`
(or `07-milestones.md` for cards 17 doesn't supersede) is the spec; its
**Done-when is the exit criterion — verified by committed test or by hand, never assumed.**

## 1. Load context (in this order, nothing more)

1. `CLAUDE.md` → **Current state** (what's built, gotchas) + **Likely next** (the pre-digested card).
2. The milestone card in doc 17 — note its **Paste:** list.
3. Only the `project-graph/` nodes the milestone touches (hub → follow wiki-links).
4. Only the doc sections the card's Paste list names. Do NOT read whole docs.
5. Existing code idioms: read 1–2 neighboring classes of whatever you're about to write
   (e.g. writing a controller → read an existing controller + its DTOs + its test).

## 2. Branch & docs-first

- Branch `m<id>-<slug>` (e.g. `m0.5b-llm-loop`) off `main`. Trunk-based, squash-merge at the end.
- **Docs-first (09 rule 2):** if the card requires a decision the contracts don't answer,
  or amends a contract ("supersedes 03's SQL"), edit the `atrium-docs/` file FIRST, then code.
- Anything marked **normative/verbatim** (13 §1.1 SPIs, 03 claim query) is copied exactly —
  renames are a contract change.

## 3. Build rules that have bitten before (check every time)

- **Tenant isolation:** every repo method takes companyId or a tenant filter (03 invariant 4).
  Tables without company_id (subtasks) scope via EXISTS-join on the parent.
- **Same-tx invariants are structural:** OutboxWriter/TaskEventRecorder use
  `Propagation.MANDATORY`; state changes go through `TaskStateGuard` (sole caller of the
  package-private status setter). Never write task_events or outbox_events any other way.
- **Cross-module calls via service interfaces only** (`AgentDirectory`, `ModelCatalogLookup`,
  `BudgetGuard`, `WorkBroker` pattern) — never another module's repository.
- **Runtime SPI lives in `registry/runtime`** (not execution) to avoid the
  execution→routing→registry→execution cycle.
- Retries for LLM calls live in `LlmRouter` ONLY (SDK maxRetries=0). Errors map to the
  13 §1.3 taxonomy — no new kinds.
- `tasks.created_by_user_id` has an FK → users; tests seed users via JDBC (no users API until Phase 3).
- Validation errors → `FieldValidationException` (400 + fieldErrors map); wrong-state → `ConflictException` (409).
- New capability = data (registry row / catalog row / config), never an `if (skill == …)` branch.

## 4. Tests

- Integration tests extend `IntegrationTestBase` (singleton pgvector+redis Testcontainers;
  `atrium.lease.reclaim-ms=1000` so scheduled jobs are observable in seconds).
- Test the Done-when literally, plus: tenant isolation (company B sees nothing),
  exact event/outbox row counts via JdbcTemplate, 400/404/409 semantics.
- Time-based behavior: force state via JDBC (e.g. expire a lease), then poll the API —
  exercise the REAL scheduled path, never call the job method directly.
- Concurrency: thread pool + TestRestTemplate; assert exact call/win counts.
- Run: `cd core-api && ./mvnw test` (Docker must be running). Then `make check` from repo root.
- LLM live tests: `@EnabledIfEnvironmentVariable(named="ANTHROPIC_API_KEY", matches=".+")` —
  written always, skipped without a key; flag the skip in the summary if the Done-when needs it.

## 5. End of session (all four, always)

1. Update `CLAUDE.md` **Current state**: new dated block on top (what/why/gotchas/Done-when
   evidence), demote the previous block to "Earlier". Rewrite **Likely next** as a
   pre-digested card for the next milestone (build list, branch name, Paste list, Done-when).
2. Update every touched `project-graph/` node's **State:** line + `milestones.md` (✅ line +
   "Likely next") + the hub `_Atrium.md` status parentheses.
3. Commit on the branch, then squash-merge:
   `git checkout main && git merge --squash <branch> && git commit && git branch -D <branch>`.
   Message: `m<id>: <title>` + body summarizing build + Done-when evidence, ending with the
   Claude co-author line. Never push unless asked.
4. Report to the user: outcome first, Done-when evidence, design calls made, anything
   deferred or needing their action (keys, manual checks).
