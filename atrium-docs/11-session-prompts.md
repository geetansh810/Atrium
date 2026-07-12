# 11 — Ready-to-Paste Session Prompts

One block per milestone. **Recipe for every session:** paste (1) the PRIMING BLOCK from `00-MASTER-PLAN.md §1`, then (2) the block below, then (3) attach/paste the listed doc sections. End every session by running the milestone's Done-when yourself.

Legend — 📎 = paste/attach these before the prompt text.

---

## M0.0 — OSS evaluation spike
📎 `06-open-source-reuse.md` (all)
```
This is a research session, not a coding session. I have cloned SkyOffice
(github.com/kevinshen56714/SkyOffice) and Paperclip locally. Walk me through
evaluating them per doc 06: for SkyOffice, identify the exact files for (a) room/
schema definitions, (b) player movement + anims, (c) chat bubbles, (d) everything
PeerJS/webcam (to strip), and produce docs/notes/skyoffice-findings.md. For
Paperclip, answer the five questions in 06 §B and produce docs/notes/
paperclip-findings.md. Also check the LimeZu asset license status for commercial
hosting. Flag anything that should change our specs (03/04/05).
```

## M0.1 — Repo scaffold & data model
📎 `02-architecture.md §2`, `03-data-model.md` V1 section, `08-conventions.md`
```
Implement milestone M0.1. Create the monorepo layout from 02 §2. Spring Boot 3.x
(Java 17, Maven) service `core-api` with actuator health endpoint; Flyway migration
V1__core.sql containing exactly the Phase-0 tables from 03 (V1 section, verbatim
where SQL is given); docker-compose.yml with postgres:16, redis:7, core-api;
Makefile with dev/test/check targets; .env.example per 08. Package-by-module
skeleton: registry, routing, execution, accountability, realtimebridge, common —
empty services with interfaces only. Do not implement business logic yet.
Output: file tree, all files, and the command sequence to verify
(docker compose up → health 200 → flyway clean).
```

## M0.2 — Registry module
📎 `03-data-model.md` (companies, users, role_definitions, agents), `04-api-contract.md` §Registry, `05-module-specs.md` §registry
```
Implement milestone M0.2 inside core-api/registry only. Endpoints per 04 §Registry:
create company, hire agent (template key or explicit role_definition), roster,
patch agent, role-definitions list/create. Seed 3 global role templates via a
V2 migration: coder, tester, research (write sensible system prompts). Validate:
skill_tags non-empty, manager hierarchy acyclic. Dev-auth via X-Company-Id/X-User-Id
headers resolved in common/TenantContext. Tests: hire-with-template resolves a
role_definition; cycle in manager graph is rejected. Do not touch routing/execution.
```

## M0.3 — Task creation & skill routing
📎 `03` (tasks, subtasks, task_events), `04` §Tasks (create/list/get/events), `05` §routing
```
Implement milestone M0.3 in core-api/routing. Task create (with optional subtasks),
company-scoped list with filters, get with subtasks, events endpoint. Validate
required_skill exists on at least one roster agent (via registry's AgentDirectory
interface — do not import registry internals). Append task_events(created) in the
same transaction. Publish nothing yet. Tests: the two-company isolation test —
company B can never see company A's task through any endpoint.
```

## M0.4 — Claim loop & lease
📎 `03` §claim query + reclaim, `04` (claim, lease/renew), `05` §routing
```
Implement milestone M0.4 in core-api/routing. The claim endpoint must use the
canonical SKIP LOCKED query from 03 verbatim. Lease renewal endpoint. @Scheduled
reclaim job (60s) requeueing expired leases with task_events(requeued). Worker
authentication dev-mode via X-Agent-Id. REQUIRED TEST: spawn 3 concurrent claimers
against 20 queued tasks; assert exactly-once claiming; commit this test. Also test:
expired lease gets requeued and is claimable again.
```

## M0.5 — Execution: first real agent
📎 `02` §6, `03` (usage_records, artifacts), `05` §execution, `08` §Security
```
Implement milestone M0.5 in core-api/execution. LlmClient interface + one real
provider implementation (Anthropic REST; key from env, never logged). AgentRunner:
poll → claim (via routing's service interface) → assemble prompt from
role_definition.system_prompt + task title/description (+ rejection feedback when
present) → call LLM → insert usage_records with idempotency_key "taskId:attempt"
in the same transaction as marking progress → store artifact → complete →
status pending_review. Provider failure = flag task, never crash the runner.
Tests: idempotency key blocks a duplicate usage insert; a fake provider error
flags instead of throwing. I will run one real end-to-end call myself as the
final verification.
```

## M0.6 — Approval gate & audit
📎 `04` (approve/reject/events), `05` §routing+accountability
```
Implement milestone M0.6. approve/reject endpoints (actor = user from TenantContext),
409 on wrong current status, rejected → in_progress with feedback stored in the
reject event payload; execution must include latest rejection feedback in the next
prompt (add that to the prompt assembler). Every transition appends its event
in-transaction. Test: full event chain created→claimed→completed→rejected→
claimed→completed→approved is readable from the events endpoint in order.
```

## M0.7 — Budget enforcement
📎 `03` (budgets), `05` §accountability
```
Implement milestone M0.7. BudgetGuard.canSpend(companyId, agentId) consulted inside
the claim transaction; budgets.spent_tokens incremented in the same transaction as
each usage_records insert; over-cap claim attempt → task_events(flagged, reason=
budget_exceeded) and claim refused. PUT budget endpoint. Test: cap of N tokens,
tasks run until blocked; the flagged event exists; raising the cap unblocks.
```

## M0.8 — Minimal task board
📎 `04` (roster, tasks, approve/reject, budget), reference images NOT needed (no styling pass)
```
Implement milestone M0.8. React app in web/ (Vite + TS strict + React Query).
Pages: Roster (list agents + status), Tasks (list with status chips, create form,
approve/reject buttons on pending_review), per-agent budget bar (spent/cap).
api.ts client using dev headers from a small dev-login screen (pick company/user).
Function over form — default styling, no theme work. Done-when: I can watch the
whole loop in the browser.
```

## M1.1 — Business role via registry only
```
Implement milestone M1.1. Add a "content" role_definition (system prompt for
short-form marketing copy for an Indian sweets/namkeen brand; output contract:
plain text, <200 words, no emojis unless asked) via API or a data migration —
whichever proves the point that NO core code changes. Then: git diff core-api/routing
must be empty. Hire one agent with it and run a task through.
```

## M1.2 — Real pilot run *(process milestone — no build prompt; run the pilot per 07)*
## M1.3 — Feedback fixes
```
Here is docs/notes/pilot-friction.md [paste your notes]. Pick the highest-impact
item that is a build fix (not a prompt tweak), implement it, and update the doc
marking what was addressed.
```

## M2.1 — PM & Designer roles
```
Implement milestone M2.1 exactly like M1.1: two role_definitions ("product",
"design") with role-appropriate system prompts and output contracts, zero core
changes, one task each through the pipeline.
```

## M2.2 — Task dependencies & Task Flow API
📎 `03` (parent_task_id), `04` (flow endpoint)
```
Implement milestone M2.2 in routing. Enforce: approve blocked (409 + problem
detail) while any child task or subtask is open. /tasks/{id}/flow returns
{nodes:[{taskId,title,status,agent}], edges:[{from,to}]} for the parent graph.
Execution: allow an agent (PM role) to create child tasks via a new internal
tool call surface — POST /tasks with parentTaskId, actor=agent. Test: the M2.2
done-when scenario end to end.
```

## M2.3 — Budget ledger + analytics
📎 `03` (agent_stats_daily), `04` §Accountability, reference-2 image (analytics section)
```
Implement milestone M2.3. Accountability: rollup writer updating agent_stats_daily
on approve/reject + nightly job; four analytics endpoints per 04. Web: Analytics
panel (KPI cards, 7-day bar chart, agent performance bars, top-skills chips) and
Budget Ledger page (per-agent spend vs cap, period selector, cost-per-task table)
matching the structure of the attached reference image. Use theme.ts tokens
(create theme.ts now from 01 §4 Theme).
```

## M2.4a — SkyOffice vendor & boot
📎 `06 §A`, `docs/notes/skyoffice-findings.md`
```
Implement milestone M2.4a. Vendor the SkyOffice fork into the monorepo:
server/ → office-realtime/, client/ → web/src/office/ (keep it a separately
bootable Vite/Parcel target for now), types/ → shared-types/office/. Add both to
docker-compose. Change nothing functional. Done-when: stock SkyOffice runs from
our compose. List every file moved and any path fixes made.
```

## M2.4b — Strip & identity wiring
📎 findings note (PeerJS file list), `04` (room-token endpoint)
```
Implement milestone M2.4b. Remove all PeerJS/webcam/screen-share/whiteboard code
and UI from the fork (use the file list from skyoffice-findings). Replace the
lobby/room-picker with auto-join of Colyseus room "office:{companyId}": client
fetches POST /companies/{id}/office/room-token from core-api, sends it in join
options; office-realtime validates the token (shared HMAC secret env
OFFICE_ROOM_TOKEN_SECRET) and rejects company mismatch. User avatar display name
from the session. Test: second company's token cannot join the first's room.
```

## M2.4c — Agent avatars from real state
📎 `02 §4`, `03` (office_layout), `04` (office-state, WS events), `05` §office-realtime
```
Implement milestone M2.4c. core-api/realtimebridge: publish the 04-spec events on
Redis atrium:events:{companyId} AFTER_COMMIT. V-next migration: office_layout +
seed default layout (desks 1-8, meeting_room_alpha, focus_pod_1-3, cafe, help_desk).
office-realtime: on room create, fetch /office-state and spawn agent avatars;
subscribe to Redis; map status→location per the table in 02 §4; move avatars
(straight-line tween), set name-tag status dot color and activity bubble icon.
Done-when: approving a task in the dashboard visibly updates the office within 2s.
```

## M2.5 — Escalations + chat v1
📎 `03` V2 (channels/messages/announcements), `04` §Communication, reference images
```
Implement milestone M2.5. Migrations for channels/messages/announcements (+ seed
#general, #announcements per company). Endpoints per 04. Web: Chat panel
(channels + agent DMs), Escalations page (flagged + pending_review, deep links),
Announcements panel. core-api: agents post a status message on claim/complete/flag
(template: "Working on {title}. ETA {n} minutes."); Atrium Bot posts on approve/
reject; chat.message events flow to office bubbles via the existing bridge.
```

## M2.6 — Agent Profile & Workspace panels
📎 `04` (agent profile endpoint, tasks?view=), reference-2 image
```
Implement milestone M2.6. core-api: GET /agents/{id}/profile aggregating stats
(from agent_stats_daily), skills, current tasks, activity feed (last 20 task_events
+ presence events, humanized). Web: Agent Profile panel (opens from roster AND from
clicking an avatar in the office — wire a Phaser click → React route) and
My Workspace (three tabs; task detail with subtask checklist that PATCHes progress).
Match the attached reference structure using theme.ts.
```

## M3.1 — Auth & signup
📎 `04` §auth note, `10 §5`
```
Implement milestone M3.1. Replace dev headers: email/password signup+login issuing
JWT (userId, companyId, role); signup creates company + admin user; Spring Security
filter populating TenantContext from the token; web login/signup screens; office
room-token now derived from the JWT session. Remove X-Company-Id/X-User-Id support
entirely. All existing tests updated to authenticate properly.
```

## M3.2 — Isolation hardening
📎 `03` V3 RLS note, `08` §Security
```
Implement milestone M3.2. Enable Postgres RLS on every business table with a
company_id = current_setting('app.company_id') policy; set the setting per
transaction from TenantContext. Write the adversarial suite: for every 04 endpoint,
authenticated-as-B requests against A's resources → 404/403; Colyseus join with
wrong-company token → rejected; Redis events never crosses channels. Plant one
deliberate bypass in a test branch and confirm the suite catches it, then remove.
```

## M3.3 — Usage-based billing
📎 `10 §5`, `03` V3 billing_accounts
```
Implement milestone M3.3. Stripe: customer per company on signup, metered
subscription item; nightly job reports token usage deltas; billing page (current
period, estimate, invoices list via Stripe API). Webhook endpoint for
payment_failed → flag company + Atrium Bot notice. Test mode keys only.
```

## M3.4 — Onboarding & starter rosters
```
Implement milestone M3.4. Template packs ("Engineering pod": lead+2 coders+tester;
"Content team": PM+2 content) as data. Post-signup wizard: pick pack → agents
hired → guided "assign your first task" flow → success screen linking to the
office. Instrument the funnel with simple event logs.
```

## M3.5 — Production hardening & deploy
📎 `10` (all)
```
Implement milestone M3.5 per doc 10: GitHub Actions pipelines (PR check, staging
auto-deploy with smoke script, tag-gated prod deploy), Terraform or CDK for the
AWS shape in 10 §3, rate limiting (bucket4j) on public endpoints, JSON logging with
company/task/agent ids, CloudWatch alarms from 10 §6, THIRD-PARTY-LICENSES file +
in-app credits (SkyOffice, LimeZu, Phaser, Colyseus), k6 load test script at
early-user concurrency. Done-when: tag → live prod URL with zero console steps.
```

## M4.1 / M4.2 / M4.3 — Compliance roles & docs
```
Implement milestone M4.1 [/M4.2]: role_definition with output contract that labels
everything draft/research; a review_required flag on the role making approve
impossible for any actor except a named human user (enforced in routing, tested);
[M4.2 adds: extended event payloads capturing inputs, model, prompt version for
full reconstruction]. M4.3 is a writing session: retention policy, review-process
explainer, EU AI Act posture note into docs/compliance/.
```

---

**If any session tries to change files outside its stated module, or to weaken a tenant filter, stop it and consult 09 §"When the LLM gets stuck".**
