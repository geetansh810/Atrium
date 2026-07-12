# Atrium — Session Context

Multi-tenant SaaS where companies hire AI agents as employees: roles, skills, org hierarchy, token-budget "payroll", human approval gates, and a live 2D pixel-art virtual office (forked from SkyOffice, MIT) where agent avatar positions ARE their live status.

**Owner:** Geetansh Agrawal · **Build method:** one milestone per session, AI-assisted.

## Read this first, in this order

1. This file (you're here) — identity, state, rules.
2. `project-graph/_Atrium.md` — the hub note of the knowledge graph. Follow wiki-links **only for the nodes your task touches** instead of loading whole docs. This is the primary context-saving mechanism.
3. Full contract docs in `atrium-docs/` **only when the graph note tells you to** (schema SQL, API shapes, milestone cards).

## Current state (update this section every session)

**Last session (2026-07-13, session 6b): M0.4 done — claim loop & lease live.** Branch `m0.4-claim-loop` (squashed). **Docs-first per 17: 03 §claim query updated in-repo** — canonical SQL now carries `attempt = attempt + 1` + paused-agent `NOT EXISTS`, and a note that the Worker API endpoint keys the inner SELECT by `id=$taskId` (skill-ordered form = runner's claim-next, M0.5b). `routing/`: `WorkBroker` interface + `PostgresWorkBroker` — claim = pre-flight checks for clear 409s (agent exists in-tenant else 404, skill ∈ skill_tags, not paused, `BudgetGuard.canSpend`) then the amended canonical UPDATE via JdbcTemplate (SQL guards stay authoritative under races), `em.refresh(task)` after the JDBC write **before** recording (else the recorder would outbox a stale 'queued' status), `claimed` event actor `agent:<id>` payload {agentId, attempt, leaseExpiresAt}; 0 rows → 409 naming holder + "do not retry" (16 §5). renewLease: holder+status checked, +10min via Clock bean, **no task_event** (heartbeat ≠ state change). `LeaseReclaimJob`: @Scheduled `${atrium.lease.reclaim-ms:60000}`, `pg_try_advisory_xact_lock(0xA7121004)` singleton (xact variant — crash can't wedge), expired-lease sweep `FOR UPDATE SKIP LOCKED` (deliberately cross-tenant system infra — raw SQL, not a repo method), guard→queued + clearAssignment + `requeued` event w/ previousAgentId. `WorkerTaskController` = the Worker API gateway (X-Agent-Id). `accountability/`: `BudgetGuard` interface + `NoopBudgetGuard` (true until M0.7). `common/`: GlobalExceptionHandler now maps MissingRequestHeaderException + MethodArgumentTypeMismatchException → 400. `Task` entity: `renewLease()`/`clearAssignment()` public; status still guard-only. **Test infra: IntegrationTestBase sets `atrium.lease.reclaim-ms=1000` so reclaim tests observe the REAL scheduler path in seconds.** **Done-when verified: 26/26 tests** — 3 workers × 20 tasks exactly-once (thread pool vs TestRestTemplate; only 200/409 legal; 20 distinct wins; attempt=1 + one claimed event each), expired lease requeued by the scheduled sweep (status/assignment/lease cleared, `requeued` audit+outbox rows, actor system), attempt=2 after re-claim, 409 names holder, paused/wrong-skill 409 + unpause→claim OK, renew guards (non-holder 409, no audit rows), cross-tenant claim 404 both directions, missing X-Agent-Id 400.

**Earlier (2026-07-13, session 6): M0.3 done — tasks, routing & event backbone writer live.** Branch `m0.3-routing` (squashed). `eventbus/`: `DomainEvent` record, `Topics` (12 §4 taxonomy: task/agent/system), `OutboxEvent` entity (BIGINT identity cursor key, published_at NULL=pending), `OutboxWriter` with **`Propagation.MANDATORY`** — outbox append outside a business tx is a hard error, making the same-tx invariant structural (test asserts `IllegalTransactionStateException`). `routing/`: Task/Subtask/TaskEvent entities+repos (subtasks tenant-filtered via EXISTS-join on task — no company_id column; TaskEvent append-only, no setters); `TaskStateGuard` **in `routing.domain`** as sole caller of Task's package-private `setStatus` (matrix documented in the class: queued→claimed|cancelled … approved/cancelled terminal; pending_review→in_progress covers 04's reject-with-feedback; illegal move → ConflictException → 409); `TaskEventRecorder.record(task, type, actor, payload)` = the ONE MANDATORY-tx helper writing task_events + outbox (`task.<type>`, taskId+status merged into outbox payload); `TaskService` create (skill via AgentDirectory → 400 fieldErrors when roster lacks it; parent → billing_task_id copy + request_depth+1, root → `billToSelf()` post-persist; subtasks positioned; `created` event actor `user:<id>`|`system`), list (native keyset query `(created_at,id) < cursor` DESC; filters status/skill/agentId/view=my|assigned|completed — my needs X-User-Id, completed=approved; limit clamp 1..200 default 50), get (detail = task+subtasks+latestArtifact **null until M0.5b**), events (keyset ASC). `common/`: `PageEnvelope{data,nextCursor?}` + `KeysetCursors` (base64url `ISO|uuid`, malformed → 400). **Done-when verified: 20/20 tests** — exactly one task_events AND one outbox_events row per create (JDBC-asserted: topic, task.created, published_at NULL), tenant isolation (B: empty board, 404 on task/events/cross-create), billing chain, unknown-skill 400 + nothing persisted, cursor walk exact-cover no dupes, bad cursor/status/view → 400, guard matrix unit-pinned. **Gotcha: tasks.created_by_user_id has FK → users; a bogus X-User-Id on create = 500** (acceptable dev-phase; users API is Phase 3 — tests seed users via JDBC). `currentActivity`/profile task stats deliberately NOT wired (needs a registry→routing read path; defer to M0.8/M2.3).

**Earlier (2026-07-12, session 5): MB-0 + M0.1 + M0.2 — registry module live.** M0.2 (branch `m0.2-registry`, squashed): registry entities+repos (companyId on every method; `Agent` w/ runtime_type/runtime_config JSONB/paused, skill_tags text[] via `@JdbcTypeCode(SqlTypes.ARRAY)`); endpoints 04 §Registry + 16 §1 — POST/GET companies (create is the ONE /api route exempt from X-Company-Id), hire (roleTemplateKey→latest global template OR roleDefinitionId, exactly one), roster, PATCH agents (paused patchable; lifecycle side-effects deferred to M0.5b), profile (zeroed stats until M0.3/M2.3), role-definitions (create bumps version per company+key), `/model-catalog` (prices omitted per 16 §1). `registry/runtime`: AgentRuntime SPI verbatim 13 §3.1 + RuntimeRegistry + LlmLoopRuntimeDescriptor (validateConfig strict: 4 optional positive-int keys, unknown keys rejected; start/stop inert until M0.5b). **Design call: runtime SPI lives in registry, not execution, to avoid execution→routing→registry→execution cycle — execution implements it at M0.5b.** `common/FieldValidationException` → 400 + fieldErrors map (ConfigException extends it). `V2_1__seed.sql`: coder/tester/research global templates (real prompts/contracts) + model_catalog: claude-fable-5 (deep, 10M/50M µUSD/MTok, 1M/128K), claude-sonnet-5 (balanced, 3M/15M), claude-haiku-4-5 (fast, 1M/5M, 200K/64K). Added test-scope httpclient5 (TestRestTemplate PATCH). **Done-when verified: 9/9 tests** — company+3 agents via API/roster/template resolution, bad runtimeConfig→400 fieldErrors, catalog seeded w/o prices, tenant isolation (B sees nothing of A, cross-patch 404), manager-cycle+self-manage rejected, missing header→400.

**Earlier in session 5: MB-0 + M0.1 done — backend exists.** M0.1 (branch `m0.1-scaffold`): `core-api/` Maven scaffold — Spring Boot 3.5.6 / Java 17 (web, data-jpa, validation, actuator, data-redis, flyway+postgres), Maven wrapper vendored (only-script, Maven 3.9.9 — no system mvn needed). 8 module packages with `package-info.java` boundary docs per 12 §2. `common/`: `TenantContext` (ThreadLocal, bound by `TenantContextFilter` from `X-Company-Id`/`X-User-Id`; `/api/**` requires company header → 400 problem+json), `GlobalExceptionHandler` (RFC-7807 incl. `fieldErrors` map, `NotFoundException`/`ConflictException`), `Ids`, `ClockConfig`. Flyway `V1__core.sql` (03 §V1 verbatim + 15 §1 ALTERs folded) + `V2__agent_platform.sql` (15 §§2–4, `CREATE EXTENSION vector`). Root `docker-compose.yml` (pgvector/pgvector:pg16 + redis:7 + core-api w/ healthcheck deps), `core-api/Dockerfile` (multi-stage temurin 17). `IntegrationTestBase` (singleton Testcontainers, pgvector image + redis, `@ServiceConnection`) + `CompanySmokeTest` (3 tests). **Done-when verified:** compose boots clean, both migrations apply (compose AND Testcontainers), `GET /actuator/health`=200 (`{"status":"UP"}`), company insert+read green; `make check` green (now runs core-api verify too). JPA entities deliberately NOT created — that's M0.2; smoke test uses JdbcTemplate.

**Earlier in session 5: MB-0 done — git repo live.** `git init` on `main`, root commit `chore: init monorepo (web frontend on mocks + docs)` (151 files: web/, atrium-docs/, project-graph/, .claude/launch.json — settings.local.json gitignored). Added root `.gitignore` (node/maven/env/OS), `.env.example` (08 §Config vars + doc-17 Rev C keys, milestone-annotated), README stub, `Makefile` (`dev`/`check`/`test`; core-api & office-realtime targets no-op gracefully until those dirs exist). Done-when verified: one commit with all three trees; `make check` runs web typecheck+lint clean (3 pre-existing fast-refresh lint warnings, non-blocking). Still zero backend code / no DB.

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

**Likely next:** M0.5a — LLM provider SPI + Anthropic (deps M0.2, independent of M0.4): `execution/spi/` records+interfaces **verbatim 13 §1.1**; `LlmRouter` (model_catalog validation, `atrium.llm.timeout=120s`, error-mapping table 13 §1.3, in-attempt retry policy); `AnthropicClient implements LlmProvider` via official Java SDK (06 §C), key from `ANTHROPIC_API_KEY` only (08 §Security — secrets never meet prompts); cost from model_catalog µUSD/MTok. Tests: WireMock/recorded fixtures for happy path + every 13 §1.3 error-kind mapping; real-key integration test on `tier=fast` model, skipped in CI without key. Branch `m0.5a-llm-spi`. Paste: 13 §1 entire (normative), 08 §Security. Done-when: real-key test returns text + nonzero token counts; every 13 §1.3 row has a mapping test.

## Commands

```bash
cd web && npm run dev        # dev server on :5173
cd web && npx tsc -b         # typecheck
cd web && npm run lint       # oxlint
cd core-api && ./mvnw test   # backend tests (Docker must be running — Testcontainers)
docker compose up            # pgvector + redis + core-api on :8080
make check                   # web typecheck+lint + core-api verify
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
