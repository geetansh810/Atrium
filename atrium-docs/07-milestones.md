# 07 — Milestones (Rev. B — 26 milestones, 5 phases)

One milestone = one LLM coding session. Verify **Done when** yourself before the next session. Paste the milestone card + the priming block + relevant contract sections into each session.

Changes from Rev. A: added **M0.0** (Paperclip spike), replaced monolithic M2.4 with **M2.4a/b/c** (SkyOffice fork), added **M2.6** (Agent Profile & Workspace panels), renumbered nothing else.

> **Rev D structure note (2026-07-17):** work that is **not in any phase's sequence now lives at the bottom of this file**, in two sections — **DEFERRED — post-pilot backlog** (real work, no slot: M3.3) and **RETIRED — cancelled tracks** (dead, do not build: M2.4a/b/c, the SkyOffice fork). Previously both sat inline among live milestones as annotated cards, which made cancelled and deferred work read like part of the sequence. The phases now contain only work that is done or actually next. Numbering gaps (M2.3 → M2.5, M3.2 → M3.4) are intentional and signposted.
>
> **Rev D (2026-07-17):** **Monetization is deferred until after the pilot** (owner decision — the product is being piloted, not sold, and nothing about charging real money needs to be true for a pilot to succeed). **M3.3 (Stripe metered billing) is moved out of Phase 3** into the new "Deferred — post-pilot backlog" section at the bottom of this file; its card text is preserved verbatim so a future session can pick it up unchanged. Phase 3 is now 4 milestones (M3.1, M3.2, M3.4, M3.5) and M3.5's deps drop M3.3 — you still ship a real hardened prod URL for the pilot, it just doesn't charge anyone. Token budgets/payroll (M0.7, M2.3) are **unaffected and stay core** — they are a control and accountability surface, not a monetization one. Schema follow-on: 03 §V3's `billing_accounts` and the RLS policies are split apart (see 15 §0), so M3.2's migration carries RLS only.
>
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

## PHASE 2 — EXPAND (5)

> M2.4a/b/c (the SkyOffice office-view fork) used to sit between M2.3 and M2.5 — hence the numbering gap. They were retired 2026-07-15 and moved to "RETIRED — cancelled tracks" at the bottom of this file on 2026-07-17. Nothing here depends on them.

**M2.1 — PM & Designer roles** · deps: Phase 1
Two role_definitions + queues; role-appropriate prompts.
✅ Both claim and complete through unmodified core.

**M2.2 — Task dependencies & Task Flow API** · deps: M2.1
parent_task_id semantics; approval blocked while children open; `/tasks/{id}/flow` graph endpoint.
✅ PM task spawns coder+designer children; parent approvable only after both; flow endpoint returns the graph.

**M2.3 — Budget ledger UI + analytics rollups** · deps: M0.7
agent_stats_daily rollups; analytics endpoints (summary, 7d, performance, top-skills); ledger + analytics dashboard panels per reference image.
✅ "What did this agent cost last week?" answerable in the UI; KPI cards match a hand-checked query.

**M2.5 — Escalation surface + chat v1** · deps: M2.1 · **DONE 2026-07-16 (session 24)** — new `communication` module (V7 channels/messages/announcements), `ChatNoticePipeline` (durable consumer: `task.completed` → bot message in `#general`), `GET /companies/{id}/escalations`, and frontend chat wired to the real API. The office-bubble clause was voided when the office track retired (M2.4a); only the chat-message half of the Done-when applies.
Escalations page (flagged + pending_review, 2 clicks from anywhere); channels/messages API; chat panel; Atrium Bot notices; agent status lines posted to chat ~~and mirrored as bubbles~~ (bubbles void — office retired).
✅ Every flagged task reachable in ≤2 clicks (ReviewInbox + `/escalations`); an agent completing work produces a chat message (live-verified: Priya → "🤖 Priya finished '…' — ready for review" in `#general`).

**M2.6 — Agent Profile & My Workspace panels** · deps: M2.3, M2.5 · **DONE 2026-07-16 (session 25)** — Workspace/task-detail half was already complete since MF-3's `TaskDrawer`; this session's real work was fixing Profile's three stale tabs: a new Activity tab (task_events scoped to the agent), Performance now reading real M2.3 `agent-performance` data instead of a stuck-at-zero placeholder, and Memory wired to the real M-MEM1/M-LN1 `/memories?agentId=` endpoint instead of a stale "pending" stub. Zero backend code touched.
Profile panel (stats, skills, current tasks, activity feed from task_events); Workspace tabs + task detail with subtask checklist per reference images.
✅ Both panels match reference structure; activity feed shows real events; subtask checks update progress.

## PHASE 3 — MULTI-TENANT (4)

> M3.3 (Stripe metered billing) used to live here. It moved to "Deferred — post-pilot backlog" below on 2026-07-17 (Rev D) — the pilot doesn't charge anyone, so nothing in this phase blocks on it.

**M3.1 — Auth & self-serve signup** · deps: Phase 2
✅ Fresh browser → signup → empty roster, unassisted. Dev headers removed.

**M3.2 — Isolation hardening** · deps: M3.1 · **DONE 2026-07-17 (session 31)** — `V10__tenant_rls.sql` enables + forces Postgres RLS on every company_id-bearing table, keyed off `app.company_id`/`app.bypass_rls` session GUCs stamped by a new `TenantAwareJpaTransactionManager`; a least-privilege `atrium_app` role (not the bootstrap superuser, which Postgres refuses to demote) is what the app's own datasource connects as. New `IsolationHardeningTest` — company B 404s against every one of company A's resources across ~25 endpoints + the worker gateway + Redis channel isolation; a deliberately planted bypass (dropped tenant filter + forced RLS bypass) confirmed the suite goes red, then reverted. See CLAUDE.md session 31 and `project-graph/data-model.md`/`core-api.md` for the full build + verification evidence.
Postgres RLS on all business tables; adversarial cross-tenant test suite (API + Redis channel). ~~Colyseus join~~ (void — office track retired 2026-07-15, M2.4a; there is no Colyseus room to join). The migration for this card is **RLS policies only** — `billing_accounts` is deferred with M3.3 (see 15 §0).
✅ Suite passes; a deliberately planted bypass attempt fails.

**M3.4 — Onboarding & starter rosters** · deps: M3.1 · **DONE 2026-07-17 (session 30)** — `V9__seed_starter_roster_templates.sql` seeds 2 real installable packs' worth of global role templates (`lead`/`product`/`content`, alongside M0.2's `coder`/`tester`/`research`); frontend `OnboardingWizard` (post-signup only) drives pick-pack → sequential hire with manager hierarchy → guided first task → success screen linking to Team view. See `project-graph/milestones.md` and `web-dashboard.md`/`registry.md` for the full build + live Done-when evidence.
2–3 installable templates ("Engineering pod", "Content team"); guided first task.
✅ Non-technical tester: signup → first assigned task <10 min, unassisted.

**M3.5 — Production hardening** · deps: M3.1, M3.2, M3.4 (**not** M3.3 — billing deferred, Rev D)
Rate limiting, structured logs, error alerting, load test at realistic early concurrency, AWS deploy per 02 §7, THIRD-PARTY-LICENSES + credits screen. (The LimeZu paid-asset license launch-blocker from 06 §D no longer applies — assets removed with the office-track retirement, 2026-07-15.) **Ships unmetered:** a real hardened prod URL the pilot runs on, with no payment path — no Stripe keys, no billing page, no plan gating. Whoever picks up M3.3 later adds charging on top of this, not into it.
✅ Load test passes; a triggered error alerts within 1 min; prod URL live.

## PHASE 4 — COMPLIANCE (3)

**M4.1 — Legal role, gated** — output labeled draft/research; structurally impossible to deliver without named human sign-off. ✅ Proven by test.
**M4.2 — HR role, audited** — full reconstructable audit on any HR task; mandatory oversight gate on hiring/performance/termination-adjacent tasks. ✅ Sample audit trail readable by an outsider.
**M4.3 — Compliance docs** — retention policy, review-process explainer, EU AI Act posture note. ✅ Docs exist as artifacts.

---

## DEFERRED — post-pilot backlog

Real work, deliberately not sequenced. Nothing in Phases 0–4 depends on anything here, and nothing here blocks a pilot. Each card is preserved as written so a future session can pick it up unchanged — treat these as ready-to-run milestone cards without a slot, not as sketches.

**M3.3 — Usage-based billing** · deps: M3.1, M2.3 (both satisfied) · **DEFERRED 2026-07-17 (Rev D)**
*Why deferred:* owner decision — Atrium is being piloted, not sold. Charging real money is the one Phase-3 concern with no pilot value, and building it early would mean carrying Stripe keys, a webhook surface, and a payment-failure state machine through every pilot iteration for no feedback in return. Deferring costs nothing: no billing code was ever written (`grep -ri stripe core-api/ web/` is empty as of 2026-07-17), and the card's deps were already green, so it can be picked up cold whenever charging becomes real.
*Card, unchanged:* Stripe metered billing from spent_tokens; billing page.
✅ Done when: Test-company tasks → matching Stripe line item.
*What it will need on pickup:* 03 §V3's `billing_accounts` table (deferred alongside this card — see 15 §0's billing migration row, which is deliberately unnumbered until then); the session prompt in `11-session-prompts.md`; `plan_tier` on `companies` already exists in the applied V1 schema and is already surfaced read-only on the Settings page — it is inert, not wired to any entitlement, and gating on it is part of this card, not a prerequisite.
*What is NOT part of this and must not be confused with it:* token budgets/caps/`usage_records`/the payroll ledger (M0.7, M2.3) are **core product and already live** — they meter spend for control and accountability, which a pilot needs. `billing_task_id` (15 §1) is cost *attribution* up a delegation chain, not money. None of that is monetization.

**Per-company BYO LLM keys** · deps: none · deferred
Noted in 10 §5 as a Phase-3+ backlog item. It was parked partly because it changes billing math — with billing itself deferred, that particular objection is void, so this is now a plain product call (isolation + key-handling work) whenever a pilot company asks to bring its own key.

---

## RETIRED — cancelled tracks

Dead, not deferred. Kept as tombstones so the numbering gaps above make sense and so nobody re-proposes a track that was already tried and cut. **Do not build these.** Moved here from inline in Phase 2 on 2026-07-17 — they had been sitting between M2.3 and M2.5 as retired-in-place cards, which made a cancelled track read like part of the sequence.

**M2.4a — SkyOffice vendor & boot** · **RETIRED 2026-07-15 (session 23)**
The SkyOffice pixel-art office view was removed entirely — owner decision: gimmicky, no real usage (avatars sat static; nothing ever auto-updated `agent.status`). Replaced by the live **Team view** (`/team`), which derives every agent's zone from live task state via `shared/selectors.ts`'s `liveTeamZones`. The vendored client (`web/src/office/`, 21 files), the `phaser` dependency, and 1.7MB of LimeZu assets were all deleted; `office-realtime` was never vendored and never will be. **The LimeZu non-commercial-license launch blocker from 06 §D is void** — there are no assets to license.

**M2.4b — Strip & identity wiring** · **RETIRED 2026-07-15** — see M2.4a. No Colyseus server was ever vendored, so there is no room to join and no `OFFICE_ROOM_TOKEN_SECRET` to rotate.

**M2.4c — Agent avatars driven by real state** · **RETIRED 2026-07-15** — see M2.4a. The `realtimebridge` → Redis relay (M0.75) **stays live and generic** — it simply has no office subscriber anymore. The `office_layout` table was dropped from V7 and never applied.

*Still-open thread this track leaves behind, worth knowing:* `agent.status_changed` (12 §4) has no publisher. The Team view's task-derived zones are live via polling, but its status-derived zones (`in_focus`/`offline`) still only move on a manual PATCH. That gap predates the retirement and outlived it.

---

## Master tracker

> Kept in sync manually and only occasionally — for the actually-current status, prefer root `CLAUDE.md` ("Current state"/"Likely next") and `project-graph/milestones.md`, which are updated every session. Last refreshed **2026-07-17 (Rev D, monetization deferral)** — which also caught **M3.1 still showing ☐ despite shipping at session 29**, the same periodic staleness this file's own header warns about (M-LN2 had the identical problem at the previous refresh). M3.3 is sorted to the bottom of the table because it is no longer part of any phase's sequence.

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
| M2.5 | Escalations + chat v1 | 2 | ✅ |
| M2.6 | Profile & Workspace panels | 2 | ✅ |
| M3.1 | Auth & signup | 3 | ✅ (session 29 — row was stale at ☐ until 2026-07-17) |
| M3.2 | Isolation hardening | 3 | ✅ |
| M3.4 | Onboarding & templates | 3 | ✅ |
| M3.5 | Production hardening | 3 | ☐ ← next |
| M4.1–M4.3 | Compliance roles & docs | 4 | ☐ |
| | *— not in any phase's sequence; see the DEFERRED / RETIRED sections above —* | | |
| M3.3 | Usage-based billing | ~~3~~ backlog | ⏸ deferred 2026-07-17 — post-pilot |
| M2.4a | SkyOffice vendor & boot | ~~2~~ — | ✗ retired 2026-07-15 |
| M2.4b | Strip & identity wiring | ~~2~~ — | ✗ retired 2026-07-15 |
| M2.4c | Agent avatars, real state | ~~2~~ — | ✗ retired 2026-07-15 |

**Frontend redesign series (MF-1…MF-6, not part of the 26 above — see `project-graph/milestones.md`): ✅ all complete.** Ran interleaved with the depth series (2026-07-14, sessions 7–12); supersedes the office-canvas-first dashboard with the routed Mission-Control-style shell. Not tracked in `atrium-docs/` — full plan lives at `/Users/geetanshagrawal/.claude/plans/multi-agent-employee-platform-redesign-fluttering-crescent.md`.
