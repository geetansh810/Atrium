# Atrium — Session Context

Multi-tenant SaaS where companies hire AI agents as employees: roles, skills, org hierarchy, token-budget "payroll", human approval gates, and a live 2D pixel-art virtual office (forked from SkyOffice, MIT) where agent avatar positions ARE their live status.

**Owner:** Geetansh Agrawal · **Build method:** one milestone per session, AI-assisted.

## Read this first, in this order

1. This file (you're here) — identity, state, rules.
2. `project-graph/_Atrium.md` — the hub note of the knowledge graph. Follow wiki-links **only for the nodes your task touches** instead of loading whole docs. This is the primary context-saving mechanism.
3. Full contract docs in `atrium-docs/` **only when the graph note tells you to** (schema SQL, API shapes, milestone cards).

## Current state (update this section every session)

**Last session (2026-07-12, session 5): MB-0 done — git repo live.** `git init` on `main`, root commit `chore: init monorepo (web frontend on mocks + docs)` (151 files: web/, atrium-docs/, project-graph/, .claude/launch.json — settings.local.json gitignored). Added root `.gitignore` (node/maven/env/OS), `.env.example` (08 §Config vars + doc-17 Rev C keys, milestone-annotated), README stub, `Makefile` (`dev`/`check`/`test`; core-api & office-realtime targets no-op gracefully until those dirs exist). Done-when verified: one commit with all three trees; `make check` runs web typecheck+lint clean (3 pre-existing fast-refresh lint warnings, non-blocking). Still zero backend code / no DB.

**Session 4 (2026-07-12):** Backend fully planned — Rev C "agent platform" doc set written (docs only, zero code, deliberate plan-first decision). Any later session can generate backend code from the docs alone; only grunt work + tests remain.

- ✅ Research mined into `atrium-docs/notes/`: `paperclip-findings.md` (adapter SPI, skills-as-data, two-layer memory model, budget tiers, checkout semantics — **M0.0's Paperclip half DONE**) + `solace-agent-mesh-findings.md` (topic taxonomy, AgentCards, gateways, orchestrator-as-agent).
- ✅ New contract docs `atrium-docs/12–17`: 12 event-driven architecture (new `agentmind` + `eventbus` modules, transactional outbox → Redis relay, topic taxonomy, scalability path); 13 normative LLM provider SPI + `model_catalog` + AgentRuntime SPI (`llm_loop` built-in; webhook/process later — any LLM / agent type without schema breaks); 14 skills/memory/learning subsystem (the differentiator: skills registry, pgvector MemoryStore SPI, ContextAssembler, governed LearningPipeline); 15 schema delta + **migration renumbering** (V1 core+ALTERs, V2 agent platform; old V2/V3 ship as V3/V4; compose image → pgvector/pgvector:pg16); 16 API delta (skills/memories/review-queue/model-catalog + Worker API gateway); 17 **backend execution plan** — per-session cards MB-0 → M0.x(amended) → M-SK1/CTX1/MEM1/LN1/KN1/AR1/LN2 with Done-whens, test matrix, config keys.
- ✅ Rev C pointers stitched into docs 00/03/04/05/07; graph updated: new node `agent-platform` + state lines in hub, core-api, execution, routing, registry, accountability, data-model, milestones, realtimebridge.
- ❌ Still no git repo, no backend code, no DB. Frontend unchanged (feature-complete on mocks).

**Session 3 (2026-07-12):** Office canvas live — SkyOffice client vendored into `web/src/office/` (upstream commit `3f66b8b`, MIT) and driven entirely by the mock store. Frontend is now feature-complete on mocks.

- ✅ Vendored fork (provenance + kept/stripped/added ledger: `web/src/office/README.md`): Phaser 3.90, scenes/characters/items/anims kept; Colyseus/PeerJS/webcam/whiteboard/lobby/Redux stripped at vendor time. Assets (1.7MB, LimeZu) in `web/public/assets/`.
- ✅ Atrium additions: `bridge.ts` (React↔Phaser doorway, future Colyseus swap point), `officeLayout.ts` (locationKey/status → seat coords; seed for the future `office_layout` table), `AgentAvatar` (walks to status seats, status dot from CSS vars, activity bubble, click → profile panel), `OfficeCanvas.tsx` mount (OfficePlaceholder deleted).
- ✅ Wiring: sidebar room nav pans the office camera (movement keys hand it back to the player); WASD/arrows + E-to-sit; typing in dashboard inputs never moves the avatar.
- ✅ Verified in browser: 6 agents seated per mock locationKeys with status dots; avatar click opens AgentProfile; **Exit Pod makes CoderAgent walk pod → desk_1 and sit** (mock-driven analog of M2.4c's Done-when). Typecheck + lint clean, zero console errors.
- ⚠️ LimeZu license verified (M2.4a obligation): free tier is NON-commercial — paid packs (~$1.50+ each) must be bought before commercial launch. Ledger updated in 06 §D; gate at M3.5.
- ⚠️ Boot hardening in `OfficeCanvas`/`createGame`: game creation deferred until the container has real size, plus a texture-READY watchdog (embedded-browser quirks; no-op in normal Chrome). Phaser's loop freezes in hidden tabs by design and resumes on focus.
- ❌ No backend, no git repo, no office-realtime (SkyOffice `server/` not vendored yet — that happens at real M2.4a). Mock-data caveats from session 2 unchanged.

**Session 2 (2026-07-12):** Full dashboard on JSON mocks — mock layer (`mocks/*.json` → `mockData.ts` → `store.tsx`, the API swap point), all reference-2 panels + modals, shell wired to store. No tests (deliberate).

**Likely next:** M0.1 — core-api Maven scaffold, Flyway V1/V2 migrations, docker-compose (pgvector:pg16 + redis:7), Testcontainers smoke test. Branch `m0.1-scaffold` per git conventions. Follow `atrium-docs/17-backend-execution-plan.md` cards in order, one per session.

## Commands

```bash
cd web && npm run dev      # dev server on :5173
cd web && npx tsc -b       # typecheck
cd web && npm run lint     # oxlint
```

Browser preview: `.claude/launch.json` has a `web-dev` config (uses cwd `web`, port 5173).

## Hard rules (from atrium-docs — violating these means the session is wrong)

- **Tenant isolation everywhere.** Every query/endpoint company-scoped. Cross-tenant access must be impossible.
- **Claiming:** Postgres `FOR UPDATE SKIP LOCKED` + lease. Idempotency keys (`taskId:attempt`) — a redelivered task never double-bills an LLM call.
- **Append-only audit:** every task state change = `task_events` row in the same transaction.
- **Nothing ships unreviewed:** `pending_review` is a hard gate.
- **Roles are data:** new capability = registry row + queue. `if (skill == …)` in routing is a bug by definition.
- **Secrets never meet prompts:** PromptAssembler sees only role definition + task + feedback.
- **Office = projection:** office-realtime never writes business state; rebuilds from `GET /office-state`.
- **Office map is a FORK of SkyOffice (M2.4a–c), never rebuilt by hand.** The dashboard placeholder marks where it mounts.

## Conventions (short form — full: atrium-docs/08-conventions.md)

- Java 17 / Spring Boot 3.x / Maven, constructor injection, records for DTOs, no Lombok in entities, package-by-module (`registry|routing|execution|accountability|realtimebridge|common`).
- TS strict, no `any`, function components + hooks; React Query for dashboard data (not yet added — mock data currently imported directly); Redux Toolkit only inside the future office slice.
- Flyway only for SQL (`V<N>__desc.sql`), never edit applied migrations. DB snake_case, JSON camelCase, events dot.case.
- Frontend types live in `web/src/shared/types.ts` and mirror 03-data-model columns — never redeclare shapes ad hoc. All colors/fonts from `theme.ts` / CSS vars — no hardcoded hex in components.
- Git (once initialized): trunk-based, branch per milestone (`m0.4-claim-loop`), squash-merge, commit prefix = milestone id.

## End-of-session checklist

1. Update **Current state** above (last session, ✅/❌, likely next).
2. Update the affected `project-graph/` node notes (state lines + links).
3. Run the milestone's Done-when check manually with the user.
