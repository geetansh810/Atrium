# 08 — Conventions & Standards

## Code style
- **Java:** 21 (LTS — 12 §8/13 §3.2 run the agent runner on virtual threads, a Java 21 feature; amended from 17 at M0.5b), Spring Boot 3.x, Maven. Constructor injection only. Records for DTOs. No Lombok in domain entities (explicitness beats brevity for LLM sessions). Package-by-module (registry/routing/execution/accountability), never package-by-layer.
- **TypeScript:** strict mode, no `any`. React function components + hooks only. State: Redux Toolkit in the office slice (inherited from SkyOffice), React Query for dashboard API data. Types imported from `shared-types/` — never redeclared.
- **SQL:** Flyway only (`V<N>__description.sql`), never edit an applied migration, never `ddl-auto`.
- **Naming:** DB snake_case; Java camelCase; API JSON camelCase; events dot.case (`agent.status_changed`).

## Repo & git
- Trunk-based: short-lived branch per milestone → PR → self-review with the checklist in 09 → squash-merge. Branch name = `m0.4-claim-loop`. Commit prefix = milestone id.
- Every merge leaves `main` bootable: `make dev` and `make test` must pass.
- `make check` = lint + typecheck + tests, runs in CI (GitHub Actions) on every PR.

## Testing bar (right-sized, not aspirational)
- **Must-test (blocking):** claim concurrency (3×20 exactly-once), tenant isolation (cross-company access attempts), idempotent usage recording, approval invariants (open children block approve), budget cap blocking, lease reclaim.
- **Should-test:** every endpoint happy path + one 4xx (Spring `@WebMvcTest` or REST-assured), event publication after commit.
- **Skip for now:** UI snapshot tests, load tests before M3.5, exhaustive branch coverage.
- Test containers (Postgres, Redis) for integration tests — no H2.

## Security rules (apply from M0.1, not later)
1. Secrets only via env vars → Spring config; never committed, never logged, never in prompts.
2. Agent prompts are built from role_definitions + task fields only. Task descriptions are **untrusted input** — treat agent output touching tools/repos as untrusted too (prompt-injection is a known live attack in this ecosystem).
3. Company scoping in every repository method signature (invariant #4 in 03).
4. ~~Colyseus join requires a core-api token~~ — void, the office track retired 2026-07-15 (07 M2.4a), no Colyseus server was ever vendored.
5. Dependency updates: `npm audit`/`mvn versions` check monthly.
6. **M3.2 — Postgres RLS is the second enforcement layer, not the only one.** Every RLS-eligible table carries `ENABLE` + `FORCE ROW LEVEL SECURITY` and a `company_id = current_setting('app.company_id', true)::uuid` policy (`current_setting(..., true)` — the `true` means "don't error if unset," so a request with no tenant bound sees zero rows, fail-closed). Two Postgres session GUCs drive it, both set once per transaction by `common.TenantAwareJpaTransactionManager` (a `JpaTransactionManager` subclass overriding `doBegin`, since `SET`/`set_config` can't run before a transaction/connection exists):
   - `app.company_id` — read from `TenantContext` when bound (an HTTP request's JWT, or a background job's `TenantContext.runAsSystem(companyId, ...)`).
   - `app.bypass_rls` — `'on'` only inside `TenantContext.runWithBypass(...)`, used by the handful of components that are *genuinely* cross-tenant by design: `LeaseReclaimJob`, `OutboxRelay`, `OutboxRetentionJob`, `MemoryTtlArchiver`, `StatsRollupReconciliationJob` (all pre-existing, all already documented as "system infra, not a tenant read path"), `EventCursorWorker.pollOnce` (durable consumers poll a batch across every company's outbox rows, then each `handle()` call still writes company-scoped via the event's own `companyId`), and `AuthService.signup`/`login` (pre-auth — company creation and cross-company email lookup happen before any tenant is known). Every other path (HTTP controllers via `TenantContextFilter`, the Worker API gateway via `WorkerTaskController` binding `TenantContext.runAsSystem` around `companyIdOf(agentId)`, `LlmLoopRuntime`/`EchoRuntime`'s per-agent poll via the same) sets `app.company_id` to a real value, never bypass.
   - **Known dev/test caveat, not a prod concern:** Postgres RLS never applies to a `SUPERUSER` role regardless of `FORCE`, and the official `postgres` Docker image's `POSTGRES_USER` bootstrap role IS a superuser — true for both `docker-compose.yml`'s `atrium` user and Testcontainers' auto-generated role, and Postgres refuses to ever strip `SUPERUSER` from that bootstrap role even by its own hand ("the bootstrap user must have the SUPERUSER attribute"), so demoting the connecting role in-migration is impossible. `V10__tenant_rls.sql` instead does a genuine privilege split: it creates a new least-privilege `atrium_app` role (`NOSUPERUSER NOBYPASSRLS`, granted everything) that the app's own `spring.datasource` connects as, while Flyway keeps the bootstrap `DATABASE_USER` credentials (needs real DDL rights to create that role in the first place). **AWS RDS's master user is never a true Postgres superuser** (RDS explicitly withholds the `SUPERUSER` attribute even from its admin role), so this split is a local-dev/CI correctness requirement, not an RDS concern — M3.5's real RDS deployment enforces RLS correctly with zero extra role plumbing.
8. **M4.1 — Compliance-gated roles cannot be self-approved by anything but a named human.** `role_definitions.review_required` (default false) marks a role whose work always needs a human sign-off before it ships — the seeded global `legal`/`hr` templates set it `true`. `TaskService.approve` resolves the task's assigned agent → its role definition and, when `review_required`, requires `TenantContext.userId()` to be bound (a real authenticated human — never `agent:` and never a `system`-actor background/test call via `TenantContext.runAsSystem`) or throws `ForbiddenException` (403). This sits alongside, not instead of, the existing open-children/open-subtasks gate (invariant 5) — both must pass before `TaskStateGuard.transition(task, "approved")` runs. The gate reads the role of whoever is ASSIGNED to the task, not who's approving — a legal-role agent's draft always needs a human, regardless of who happens to be logged in.
9. **M3.5 — Rate limiting is Redis-backed, not in-process.** ECS runs ≥2 `core-api` tasks behind the ALB (10 §3), so an in-memory counter would let each task grant its own separate quota to the same caller; a fixed-window counter in the ElastiCache Redis every task already shares (`StringRedisTemplate`, already a dependency since M0.75's outbox relay) closes that gap for free. `common.RateLimitFilter` (a second `OncePerRequestFilter`, ordered right after `TenantContextFilter` so it can read the bound `TenantContext`) scopes `/api/**` traffic three ways — company id once a tenant is bound (the normal case), agent id for the Worker API gateway (`X-Agent-Id`, its own axis per rule 4 below), client IP for anything pre-auth (`/auth/signup`, `/auth/login` — the actual abuse surface, since there's no tenant yet to scope by) — and rejects over-limit requests with `429` + `Retry-After` rather than a bare connection drop. Config: `atrium.rate-limit.enabled`/`default-per-minute`/`auth-per-minute`/`worker-per-minute` (`ATRIUM_RATE_LIMIT_*` env). Test-profile default is effectively unlimited (`application-test.yml`) — the whole integration suite shares one Testcontainers Redis, so a tight limit would throttle unrelated tests' repeated `signup()` helper calls exactly the way an always-on `OutboxRelay` would have raced a different context's assertions (this file's own precedent); `RateLimitFilterTest` dials it back down via `@TestPropertySource` to actually exercise the 429 path.

## Config & environments
- `.env.example` committed with every variable documented; real `.env` gitignored.
- Key vars: `DATABASE_URL, REDIS_URL, ANTHROPIC_API_KEY / OPENAI_API_KEY / GOOGLE_API_KEY, ATRIUM_JWT_SECRET, APP_BASE_URL, PRODUCT_NAME` (single rename point).
- Profiles: `dev`/`prod` both use real JWT auth now (M3.1 removed dev headers) — `dev` ships an insecure fallback `atrium.jwt.secret` in `application.yml` for local convenience only; `ATRIUM_JWT_SECRET` MUST be set in any shared/prod environment.

## Definition of done (every milestone)
1. "Done when" line verified by you, by hand or by the committed test — not assumed.
2. `make check` green on the branch.
3. No TODOs that hide missing behavior (TODO = tracked follow-up in 07, or it doesn't merge).
4. Contract files (03/04) updated **before** code if anything changed.
5. New capability arrived as data (registry row/config), not a router edit — if not, stop and refactor.
6. You read the diff. All of it.
