# 8 · Security & tenancy

Rules of record: [`08-conventions.md`](../08-conventions.md) §Security rules. Full treatment: [`18-technical-architecture.md`](../18-technical-architecture.md) §§5–6. Compliance posture: [`compliance/`](../compliance/).

## Authentication

- `POST /api/v1/auth/signup` creates the company and its first admin; `POST /api/v1/auth/login` verifies email + password (BCrypt). Both return an HS256 **JWT** with `sub` (user id), `companyId`, `role`; 30-day expiry, no refresh flow.
- Every `/api/**` call needs `Authorization: Bearer <jwt>` — a missing/invalid token is `401` problem+json. Signup/login are the only unauthenticated routes, plus the Worker API gateway below.
- [`TenantContextFilter`](../../core-api/src/main/java/app/atrium/common/TenantContextFilter.java) validates the token and binds [`TenantContext`](../../core-api/src/main/java/app/atrium/common/TenantContext.java) (company, user, role) for the request. A path company id that doesn't match the token is a `404`, not a `403` — it never confirms the resource exists.
- **Worker API gateway** (`POST /tasks/{id}/claim`, `/lease/renew`): authenticated by `X-Agent-Id` alone; the company is derived from the agent row. It is a separate axis from human auth by design.
- Secrets come from environment variables only; `ATRIUM_JWT_SECRET` ships with an insecure committed dev fallback that **must** be overridden in any shared environment.
- Authorization is coarse today: the `role` claim is carried but nothing gates on it beyond the compliance rule below.

## Tenant isolation, twice

| Layer | Mechanism | Fails how |
|---|---|---|
| 1. Application | Every repository method takes a `companyId` (or filters by tenant); tables without one scope via an `EXISTS` join to the parent | A forgotten filter would leak — hence layer 2 |
| 2. Database | Postgres **Row Level Security**: `ENABLE` + `FORCE`, policy `company_id = current_setting('app.company_id', true)::uuid`. [`TenantAwareJpaTransactionManager`](../../core-api/src/main/java/app/atrium/common/TenantAwareJpaTransactionManager.java) sets that GUC at the start of every transaction. | Fail-closed: no tenant bound ⇒ zero rows |

Details that bite:

- The app connects as **`atrium_app`** (`NOSUPERUSER NOBYPASSRLS`, created by [`V10`](../../core-api/src/main/resources/db/migration/V10__tenant_rls.sql)); Flyway connects as the bootstrap role. RLS never applies to a superuser, which is why the roles are split.
- Because the GUC is set when a transaction *begins*, any new repository call needs a real `@Transactional` boundary in its chain — without one it silently returns **zero rows**, not an error.
- Background loops bind a company with `TenantContext.runAsSystem(companyId, …)`. Only a short list of genuinely cross-tenant components use `runWithBypass` (lease reclaim, outbox relay/retention, memory TTL, stats reconciliation, durable-consumer polling, signup/login).
- Realtime events are namespaced `atrium:events:{companyId}`, so a subscriber structurally cannot receive another company's events.
- Proof: [`IsolationHardeningTest`](../../core-api/src/test/java/app/atrium/IsolationHardeningTest.java) hits ~25 endpoints with company B's token against company A's ids and expects 404 from each, plus a same-token control group; it was also shown to go red with both layers disabled.

## Guarding what agents do

- **Prompts contain no secrets.** [`PromptAssembler`](../../core-api/src/main/java/app/atrium/execution/PromptAssembler.java) sees only role definition, task fields, reviewer feedback and the context bundle.
- **Task text is untrusted input**, and so is agent output that touches tools. Today the only tool is `create_child_tasks`, offered only when the role's `allowed_tools` lists it.
- **Human gate:** nothing leaves `pending_review` except via a person; roles with `review_required` (`legal`, `hr`) additionally refuse approval from any non-human actor (`403`) — [`TaskService.approve`](../../core-api/src/main/java/app/atrium/routing/TaskService.java), proven in [`ComplianceGateTest`](../../core-api/src/test/java/app/atrium/routing/ComplianceGateTest.java).
- **Budgets** cap spend per agent and per company before a claim is allowed.

## Abuse and operations

- **Rate limiting:** [`RateLimitFilter`](../../core-api/src/main/java/app/atrium/common/RateLimitFilter.java), Redis fixed window — per company (300/min), per agent id on the worker gateway (240/min), per client IP on signup/login (10/min); `429` + `Retry-After`. Disabled under the test profile.
- **Logging:** with `SPRING_PROFILES_ACTIVE=prod`, JSON logs carry `companyId`/`userId`/`taskId`/`agentId` from MDC. Secrets are never logged.
- **Alerting:** every ERROR log line can POST to `ATRIUM_ALERT_WEBHOOK_URL` (30s cooldown, blank = off).
- **CORS:** one allowed origin, `atrium.cors.allowed-origins` (default `http://localhost:5173`) — set it to the real web origin in any deployment.

## Known gaps (do not assume these are covered)

- **A seeded admin account exists in every fresh database** ([`V15__seed_admin_account.sql`](../../core-api/src/main/resources/db/migration/V15__seed_admin_account.sql)). Remove or rotate it before any shared or production environment.
- `atrium_app`'s password is a literal in `V10`; it can be overridden by env var for the connection but the role is created with that literal ([`infra/terraform/README.md`](../../infra/terraform/README.md) "Known gap").
- No account/company deletion or data-export path ([`compliance/retention-policy.md`](../compliance/retention-policy.md)).
- No token revocation or refresh; a stolen JWT is valid for 30 days.

*Verified against:* the files linked above and [`application.yml`](../../core-api/src/main/resources/application.yml).
