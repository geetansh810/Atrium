# ATRIUM — Master Development Plan (Rev. B)

> **Working name:** Atrium · **Tagline:** Build. Automate. Scale.
> **What it is:** A multi-tenant SaaS platform where companies hire AI agents as real employees — with roles, skills, an org hierarchy, token-based "payroll" budgets, human approval gates, and a walkable 2D pixel-art virtual office where all agent activity is visible live.
> **Owner:** Geetansh Agrawal · **Build method:** AI-assisted (LLM coding sessions), one milestone at a time.

---

## 0. How to use this documentation

This package is designed so that **any single file + this master plan can be handed to any LLM** and produce code that connects correctly to the rest of the system.

**Rules for every development session:**

1. **Always paste the CONTEXT PRIMING BLOCK (section 1 below) at the start of every LLM session.** It gives the model the project identity, architecture summary, and conventions in ~300 words.
2. Then paste the **specific milestone card** from `07-milestones.md` you're building.
3. Then paste the **relevant contract sections**: schema from `03-data-model.md`, endpoints from `04-api-contract.md`, and the module spec from `05-module-specs.md`.
4. One milestone per session. Verify the milestone's "Done when" line **yourself** before moving on.
5. All generated code goes through your review before merge. No exceptions — this is the same rule Atrium enforces for its own agents.

**Document map:**

| File | Purpose | Give to LLM when… |
|---|---|---|
| `00-MASTER-PLAN.md` | This file. Index + context priming block | Every session (priming block only) |
| `01-product-spec.md` | Vision, features, full UI spec derived from reference images | Building any user-facing feature |
| `02-architecture.md` | Services, monorepo layout, data flow, realtime contract | Any backend or integration work |
| `03-data-model.md` | Complete database schema | Any work that touches data |
| `04-api-contract.md` | REST endpoints + WebSocket events | Any API/frontend work |
| `05-module-specs.md` | Per-module responsibilities and boundaries | Building inside a specific module |
| `06-open-source-reuse.md` | SkyOffice fork plan + Paperclip pattern mining | Phase 0 spike + Phase 2 office work |
| `07-milestones.md` | All 26 milestones, dependencies, done-when checks | Every session (one card at a time) |
| `08-conventions.md` | Code style, repo rules, testing, security, DoD | Every session (linked from priming block) |
| `09-llm-workflow.md` | Prompt templates and review checklist for AI-assisted building | You, before each session |
| `10-deployment.md` | Environments, AWS architecture, CI/CD, observability, backups | M2.4c staging setup · M3.5 prod |
| `11-session-prompts.md` | Ready-to-paste prompt block for every milestone | Every session — copy the milestone's block |
| `12-backend-architecture.md` | **Rev C** event-driven backend: modules, outbox backbone, topics, gateways | Any backend work (wins over 02 on conflict) |
| `13-llm-and-agent-spi.md` | Normative LLM provider SPI + AgentRuntime SPI (pluggability contracts) | execution-module work; adding providers/models/runtimes |
| `14-skills-memory-learning.md` | Agent depth: skills registry, memory SPI, learning pipeline, ContextAssembler | agentmind work; anything touching prompts |
| `15-data-model-delta.md` | Agent-platform schema + **migration renumbering** (V1–V4 by application order) | Any schema work, together with 03 |
| `16-api-contract-delta.md` | New endpoints: skills, memories, review queue, model catalog, worker gateway | API work, together with 04 |
| `17-backend-execution-plan.md` | **Backend build script**: per-session cards MB-0→M-LN2, tests, config | Every backend session — copy the card |
| `notes/paperclip-findings.md` + `notes/solace-agent-mesh-findings.md` | M0.0 research: patterns mined + deliberate divergences | Background; cited from 12–14 |
| `INDEX.html` | **Visual hub: open this first.** Step-by-step flow, overview + images, HLD/LLD diagrams, links to everything | You — it's the navigation layer |
| `assets/` | The 3 reference images + what to check against them | Any UI milestone |

---

## 1. CONTEXT PRIMING BLOCK — paste this at the start of every LLM session

```
PROJECT: Atrium — a multi-tenant SaaS where companies hire AI agents as employees.
Agents have roles, skills, budgets (token "payroll"), a manager hierarchy, and appear
in a live 2D pixel-art virtual office (forked from SkyOffice, MIT).

ARCHITECTURE (3 services in one monorepo):
1. core-api      — Java 21 / Spring Boot. Source of truth. Modules: registry (companies,
                   agents), routing (tasks, skill queues, claim/lease), execution (LLM
                   provider abstraction, agent runner), accountability (budgets, approvals,
                   append-only task_events audit log). Postgres + Redis.
2. office-realtime — TypeScript / Colyseus (forked SkyOffice server). Presence + movement
                   + chat rooms. Reads agent/task state from core-api; renders agents as
                   avatars. NEVER writes business state.
3. web            — React. Two surfaces: (a) dashboard (roster, task board, workspace,
                   analytics, budget ledger), (b) office view (forked SkyOffice Phaser 3
                   client embedded as a route).

HARD RULES:
- Tenant isolation: every query is company-scoped. Cross-tenant access must be impossible.
- Task claiming uses Postgres FOR UPDATE SKIP LOCKED with a lease (competing consumers —
  exactly one worker per task).
- Idempotency: a redelivered task must never cause a duplicate paid LLM call.
- Every task state change is an append-only row in task_events. No overwrites.
- Nothing completes without explicit approval (human or designated supervisor agent).
- New roles = registry row + queue. NEVER a router code change.
- Secrets never appear in prompts, logs, or LLM-visible context.
- Office view renders real state from core-api. No separate "office state" store.

CONVENTIONS: Java 21 (LTS — virtual threads for the agent runner, 12 §8/13 §3.2), Spring Boot 3.x, Flyway migrations, TypeScript strict mode,
React function components + hooks, REST returns problem+json errors, all IDs are UUIDs,
timestamps are TIMESTAMPTZ/ISO-8601 UTC.
```

---

## 2. What we are building (one page)

A company signs up on Atrium and builds a roster of AI employees — e.g. ResearchAgent, DataAnalyst, EmailAgent, CoderAgent, HRAgent, ReportAgent — each with a role, skill tags, an assigned LLM model, a manager, and a monthly token budget. Users assign tasks (with subtasks, ETAs, and progress), agents work them in the background, escalate when blocked, and nothing ships without approval. Every token spent is metered like payroll.

The signature surface is the **virtual office**: a top-down pixel-art floor (Meeting Room Alpha, Cafe, Focus Pods, Workstations, Server Room, Rooftop) where every agent is an avatar whose position and animation **is** its live status — at a desk means working, in the meeting room means escalated/reviewing, in a focus pod means deep work with do-not-disturb, at the help desk means waiting on a human. The user walks around as their own avatar (WASD), clicks any agent for its profile (stats, skills, activity feed, current tasks), chats in channels or DMs, and watches analytics roll up in real time.

**Differentiation** (vs. Paperclip and the self-hosted ecosystem): hosted and non-technical-friendly; verification/approval as the core feature, not an afterthought; the office as a live rendering of true system state; managed multi-tenancy with billing.

## 3. Reference images — the visual ground truth

All in `assets/`. **Check every UI milestone against these before calling it done.**

- `reference-1-office-main-view.png` — The main office screen. Left nav (Lobby, Workstations, Meeting Rooms, Cafe, Focus Pods, Server Room, Rooftop), "Who's Here" roster with statuses, Invite Agent button, top bar (time · online count), the map itself with named rooms and agent name tags + activity speech bubbles, right rail with My Tasks (task / agent / ETA) and Agent Status, bottom Atrium Bot greeting + message input + quick-action buttons.
- `reference-2-full-ui-composite.png` — All secondary surfaces: Agent Profile modal (avatar, status, role, joined date; Tasks Completed / Success Rate / Focus Time stats; About; skill chips; Current Tasks with progress bars; timestamped Activity Feed), My Workspace (My Tasks / Assigned to Me / Completed tabs; task detail with subtask checklist and % progress), Atrium Analytics (KPI cards: Total Agents, Tasks Completed, Avg Success Rate, Focus Time; 7-day bar chart; Agent Performance leaderboard; Top Skills Used chips), Chat (channels #general #announcements #dev-team #data-insights #random + agent DMs), Focus Pod detail (do-not-disturb timer, Exit Pod), Announcements panel, Task Flow graph (User Request → DataAnalyst → fan-out to ResearchAgent/DataAnalyst/ReportAgent → Report Generated).
- `reference-3-skyoffice-original.png` — Unmodified SkyOffice, the codebase we fork. Shows what we get for free: tile map, avatar movement, name tags, rooms, chairs/desks, the webcam button we'll strip.

## 4. Phase overview (detail in 07-milestones.md)

- **Phase 0 — Foundation (9 milestones):** OSS evaluation spike, then the core loop: registry → skill-routed queue → competing-consumer claim → real LLM agent → approval gate → budget enforcement → minimal task board.
- **Phase 1 — Validate (3):** one business role, a real pilot (Agrawal Namkeen), feedback fixes.
- **Phase 2 — Expand (6):** more roles, task dependencies/subtasks, budget ledger UI, **SkyOffice fork integration** (office view v1), agent profile + workspace panels, escalation surface.
- **Phase 3 — Multi-tenant (5):** auth/signup, isolation hardening, Stripe metered billing, onboarding templates, production hardening.
- **Phase 4 — Compliance (3):** gated legal role, audited HR role, compliance documentation.

## 5. Success criteria for the whole project

1. A stranger can sign up, hire 3 agents from a template, assign a task, watch it worked in the office view, approve it, and see the cost — in under 10 minutes, unassisted.
2. Zero cross-tenant data access, proven by an automated adversarial test suite.
3. Zero duplicate paid LLM calls under worker crash/redelivery, proven by test.
4. Adding a brand-new role requires no changes to core-api routing code.
5. Every task's full history is reconstructable from task_events alone.
