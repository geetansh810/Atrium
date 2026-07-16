# 07 — Milestones (Rev. B — 26 milestones, 5 phases)

One milestone = one LLM coding session. Verify **Done when** yourself before the next session. Paste the milestone card + the priming block + relevant contract sections into each session.

Changes from Rev. A: added **M0.0** (Paperclip spike), replaced monolithic M2.4 with **M2.4a/b/c** (SkyOffice fork), added **M2.6** (Agent Profile & Workspace panels), renumbered nothing else.

> **Rev C (2026-07-12):** `17-backend-execution-plan.md` is now the authoritative backend sequence — it supersedes the M0.1/M0.2/M0.4 cards below, splits M0.5 into M0.5a/b, redefines M0.8 (dashboard already exists on mocks → milestone = mock→API swap), and inserts M0.75 + the M-SK/CTX/MEM/LN/KN/AR agent-depth series between Phase 0 and the pilot. M0.0's Paperclip half is done (notes/ committed 2026-07-12); the SkyOffice half was done in the session-3 vendor work. Frontend halves of M0.8/M2.3/M2.5/M2.6 were built early (sessions 2–3).

---

## PHASE 0 — FOUNDATION (9)

**M0.0 — OSS evaluation spike** · deps: none
Clone SkyOffice + Paperclip; run both locally; write `docs/notes/paperclip-findings.md` and `docs/notes/skyoffice-findings.md` (per 06 §B). Verify LimeZu asset license status.
✅ Done when: both run locally; findings notes exist; any spec changes from findings are applied to 03/04/05.

**M0.1 — Repo scaffold & data model** · deps: M0.0
Monorepo per 02 §2; Spring Boot skeleton with health endpoint; Flyway V1 (all Phase-0 tables from 03); docker-compose (postgres, redis, core-api).
✅ `docker compose up` boots; migrations clean; `GET /actuator/health` = 200.

**M0.2 — Registry module** · deps: M0.1
Companies, users (dev-mode), role_definitions (+ 3 seeded global templates: coder, tester, research), agents CRUD, roster endpoint, manager-cycle validation.
✅ Company + 3 agents creatable via API; roster returns them; hiring with a template resolves a role_definition.

**M0.3 — Task creation & skill routing** · deps: M0.2
Tasks + subtasks endpoints; skill validation against roster; queue read; task_events(created).
✅ Task with skill "coding" is visible only in that company's coding queue — verified with a second seeded company (isolation test committed).

**M0.4 — Claim loop & lease** · deps: M0.3
Canonical claim query; lease renewal endpoint; scheduled reclaim job; task_events(claimed/requeued).
✅ Concurrency test: 3 workers × 20 tasks → each claimed exactly once (test committed, passing).

**M0.5 — Execution: first real agent** · deps: M0.4
LlmClient interface + one provider impl; agent runner worker loop; prompt assembly from role_definition; usage_records with idempotency_key; artifacts on completion.
✅ "Write a function that reverses a string" produces a real artifact from a real LLM call; usage row recorded; rerunning a redelivered task does not double-record.

**M0.6 — Approval gate & audit** · deps: M0.5
approve/reject endpoints; pending_review holding state; rejected→in_progress with feedback in next prompt; events endpoint.
✅ Completed task sits in pending_review until acted on; `GET /tasks/{id}/events` shows the full chain; a rejected task's next attempt prompt contains the feedback.

**M0.7 — Budget enforcement** · deps: M0.5
budgets table wiring; BudgetGuard at claim time; over-cap → blocked + flagged event; spend accumulates from usage_records in-transaction.
✅ Low cap + several tasks → claim blocked with a visible flagged event at the cap.

**M0.8 — Minimal task board** · deps: M0.2–M0.7
Bare React app (web/): roster list, task list with statuses, approve/reject buttons, per-agent budget bar. Dev auth headers. No styling pass.
✅ Full loop watchable in a browser end to end.

## PHASE 1 — VALIDATE (3)

**M1.1 — Business role via registry only** · deps: Phase 0
Add "content" role_definition + agent for Agrawal Namkeen use case. Zero core code changes (this is the extensibility proof).
✅ New role accepts a task through the unmodified pipeline; `git diff` on routing module is empty.

**M1.2 — Real pilot run** · deps: M1.1
A non-you reviewer at Agrawal Namkeen submits + reviews a real task via the UI.
✅ A real task approved/rejected by someone else, unassisted.

**M1.3 — Feedback fixes** · deps: M1.2
Write friction list; ship ≥1 fix.
✅ Second pilot task completes with less friction; friction list committed to docs/notes/.

## PHASE 2 — EXPAND (6)

**M2.1 — PM & Designer roles** · deps: Phase 1
Two role_definitions + queues; role-appropriate prompts.
✅ Both claim and complete through unmodified core.

**M2.2 — Task dependencies & Task Flow API** · deps: M2.1
parent_task_id semantics; approval blocked while children open; `/tasks/{id}/flow` graph endpoint.
✅ PM task spawns coder+designer children; parent approvable only after both; flow endpoint returns the graph.

**M2.3 — Budget ledger UI + analytics rollups** · deps: M0.7
agent_stats_daily rollups; analytics endpoints (summary, 7d, performance, top-skills); ledger + analytics dashboard panels per reference image.
✅ "What did this agent cost last week?" answerable in the UI; KPI cards match a hand-checked query.

**M2.4a — SkyOffice vendor & boot** · **RETIRED 2026-07-15** — SkyOffice office view removed entirely (owner decision: gimmicky, no real usage); replaced by the live Team view (`/team`, derives agent zones from live task state). The vendored client and LimeZu assets were deleted; `office-realtime` will never be vendored.

**M2.4b — Strip & identity wiring** · **RETIRED 2026-07-15** — see M2.4a.

**M2.4c — Agent avatars driven by real state** · **RETIRED 2026-07-15** — see M2.4a. The realtimebridge → Redis relay (M0.75) stays live and generic; it simply has no office subscriber anymore.

**M2.5 — Escalation surface + chat v1** · deps: M2.1 · **DONE 2026-07-16 (session 24)** — new `communication` module (V7 channels/messages/announcements), `ChatNoticePipeline` (durable consumer: `task.completed` → bot message in `#general`), `GET /companies/{id}/escalations`, and frontend chat wired to the real API. The office-bubble clause was voided when the office track retired (M2.4a); only the chat-message half of the Done-when applies.
Escalations page (flagged + pending_review, 2 clicks from anywhere); channels/messages API; chat panel; Atrium Bot notices; agent status lines posted to chat ~~and mirrored as bubbles~~ (bubbles void — office retired).
✅ Every flagged task reachable in ≤2 clicks (ReviewInbox + `/escalations`); an agent completing work produces a chat message (live-verified: Priya → "🤖 Priya finished '…' — ready for review" in `#general`).

**M2.6 — Agent Profile & My Workspace panels** · deps: M2.3, M2.5 · **DONE 2026-07-16 (session 25)** — Workspace/task-detail half was already complete since MF-3's `TaskDrawer`; this session's real work was fixing Profile's three stale tabs: a new Activity tab (task_events scoped to the agent), Performance now reading real M2.3 `agent-performance` data instead of a stuck-at-zero placeholder, and Memory wired to the real M-MEM1/M-LN1 `/memories?agentId=` endpoint instead of a stale "pending" stub. Zero backend code touched.
Profile panel (stats, skills, current tasks, activity feed from task_events); Workspace tabs + task detail with subtask checklist per reference images.
✅ Both panels match reference structure; activity feed shows real events; subtask checks update progress.

## PHASE 3 — MULTI-TENANT (5)

**M3.1 — Auth & self-serve signup** · deps: Phase 2
✅ Fresh browser → signup → empty roster, unassisted. Dev headers removed.

**M3.2 — Isolation hardening** · deps: M3.1
Postgres RLS on all business tables; adversarial cross-tenant test suite (API + Colyseus join + Redis channel).
✅ Suite passes; a deliberately planted bypass attempt fails.

**M3.3 — Usage-based billing** · deps: M3.1, M2.3
Stripe metered billing from spent_tokens; billing page.
✅ Test-company tasks → matching Stripe line item.

**M3.4 — Onboarding & starter rosters** · deps: M3.1 · **DONE 2026-07-17 (session 30)** — `V9__seed_starter_roster_templates.sql` seeds 2 real installable packs' worth of global role templates (`lead`/`product`/`content`, alongside M0.2's `coder`/`tester`/`research`); frontend `OnboardingWizard` (post-signup only) drives pick-pack → sequential hire with manager hierarchy → guided first task → success screen linking to Team view. See `project-graph/milestones.md` and `web-dashboard.md`/`registry.md` for the full build + live Done-when evidence.
2–3 installable templates ("Engineering pod", "Content team"); guided first task.
✅ Non-technical tester: signup → first assigned task <10 min, unassisted.

**M3.5 — Production hardening** · deps: M3.1–M3.4
Rate limiting, structured logs, error alerting, load test at realistic early concurrency, AWS deploy per 02 §7, THIRD-PARTY-LICENSES + credits screen. (The LimeZu paid-asset license launch-blocker from 06 §D no longer applies — assets removed with the office-track retirement, 2026-07-15.)
✅ Load test passes; a triggered error alerts within 1 min; prod URL live.

## PHASE 4 — COMPLIANCE (3)

**M4.1 — Legal role, gated** — output labeled draft/research; structurally impossible to deliver without named human sign-off. ✅ Proven by test.
**M4.2 — HR role, audited** — full reconstructable audit on any HR task; mandatory oversight gate on hiring/performance/termination-adjacent tasks. ✅ Sample audit trail readable by an outsider.
**M4.3 — Compliance docs** — retention policy, review-process explainer, EU AI Act posture note. ✅ Docs exist as artifacts.

---

## Master tracker

> Kept in sync manually and only occasionally — for the actually-current status, prefer root `CLAUDE.md` ("Current state"/"Likely next") and `project-graph/milestones.md`, which are updated every session. This table was last refreshed 2026-07-16 (session 28, after M-AR1) — also caught M-LN2 still showing ☐ despite shipping at session 26, the kind of periodic tracker staleness this file's own header already warns about.

| ID | Milestone | Phase | Status |
|---|---|---|---|
| MB-0 | Git init & monorepo shape (NEW, doc 17) | 0 | ✅ |
| M0.0 | OSS evaluation spike | 0 | ✅ (Paperclip half; SkyOffice half via session-3 vendor work) |
| M0.1 | Repo scaffold & data model | 0 | ✅ |
| M0.2 | Registry module | 0 | ✅ |
| M0.3 | Task creation & skill routing | 0 | ✅ |
| M0.4 | Claim loop & lease | 0 | ✅ |
| M0.5 | Execution: first real agent | 0 | ✅ (split M0.5a/b per doc 17, both done) |
| M0.6 | Approval gate & audit | 0 | ✅ |
| M0.7 | Budget enforcement | 0 | ✅ |
| M0.75 | Outbox relay & realtimebridge (NEW, doc 17) | 0 | ✅ |
| M0.8 | Minimal task board (REDEFINED, doc 17: mock→API swap) | 0 | ✅ |
| M-SK1 | Skills registry (NEW, doc 17) | depth | ✅ |
| M-CTX1 | ContextAssembler v1: skills into prompts (NEW, doc 17) | depth | ✅ |
| M-MEM1 | Memory store + recall (NEW, doc 17) | depth | ✅ |
| M-LN1 | Learning pipeline + review governance (NEW, doc 17) | depth | ✅ |
| M1.1 | Business role via registry | 1 | ✅ |
| M1.2 | Real pilot run | 1 | ✅ |
| M1.3 | Feedback fixes | 1 | ☐ n/a — conditional on pilot friction; M1.2's pilot run surfaced none (session 19) |
| M-KN1 | Knowledge ingestion (NEW, doc 17) | depth | ✅ |
| M-AR1 | Runtime extensibility proof (NEW, doc 17) | depth | ✅ |
| M-LN2 | Learning surfaces in dashboard (NEW, doc 17, frontend) | depth | ✅ |
| M2.1 | PM & Designer roles | 2 | ✅ |
| M2.2 | Task dependencies & flow | 2 | ✅ |
| M2.3 | Budget ledger + analytics | 2 | ✅ |
| M2.4a | SkyOffice vendor & boot | 2 | ✗ retired 2026-07-15 |
| M2.4b | Strip & identity wiring | 2 | ✗ retired 2026-07-15 |
| M2.4c | Agent avatars, real state | 2 | ✗ retired 2026-07-15 |
| M2.5 | Escalations + chat v1 | 2 | ✅ |
| M2.6 | Profile & Workspace panels | 2 | ✅ |
| M3.1 | Auth & signup | 3 | ☐ |
| M3.2 | Isolation hardening | 3 | ☐ |
| M3.3 | Usage-based billing | 3 | ☐ |
| M3.4 | Onboarding & templates | 3 | ✅ |
| M3.5 | Production hardening | 3 | ☐ |
| M4.1–M4.3 | Compliance roles & docs | 4 | ☐ |

**Frontend redesign series (MF-1…MF-6, not part of the 26 above — see `project-graph/milestones.md`): ✅ all complete.** Ran interleaved with the depth series (2026-07-14, sessions 7–12); supersedes the office-canvas-first dashboard with the routed Mission-Control-style shell. Not tracked in `atrium-docs/` — full plan lives at `/Users/geetanshagrawal/.claude/plans/multi-agent-employee-platform-redesign-fluttering-crescent.md`.
