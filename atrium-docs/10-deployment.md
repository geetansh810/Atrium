# 10 — Deployment Plan
> **Office track removed 2026-07-15** — the SkyOffice virtual office (M2.4a–c, office-realtime, office_layout, /office-state) was retired and replaced by the live Team view (`/team`) in the dashboard; see `07-milestones.md`. Office references (Colyseus, sticky sessions, office-realtime image) are void everywhere below — struck at M3.5 rather than left as historical clutter, since this doc's whole job from here on is to be the literal thing M3.5 builds against.

## 1. Environments

| Env | Purpose | Infra | When |
|---|---|---|---|
| **local** | daily development | docker-compose (postgres, redis, core-api) + `npm run dev` for web | M0.1 onward |
| **staging** | pre-release verification, pilot users | single small AWS setup, same shape as prod | M3.5 (§9 — M2.4c's staging slot was retired with the office track and never stood up; M3.5 is the first real staging) |
| **prod** | real users | AWS, per below | M3.5 |

Rule: staging and prod differ only in size and secrets — never in shape. Everything is a container.

## 2. Build artifacts
- `atrium/core-api` — the one container image: multi-stage Dockerfile, maven build → `eclipse-temurin:21-jre` runtime (Java 21 since M0.5b's virtual-thread bump; the JRE-17 line here was stale, corrected at M3.5). Tags: `m<milestone>-<shortsha>` + `latest-staging` / `latest-prod`. Never deploy `:latest` blind.
- `web` is NOT a container — §3's architecture serves it as a plain Vite static build (`npm run build`) uploaded straight to S3 behind CloudFront, no nginx/server process at all (corrected at M3.5 — this line previously said "served by nginx," inconsistent with §3's own diagram, which has no container for web).
- `atrium/office-realtime` never shipped — the office track retired 2026-07-15 before its Colyseus server was ever vendored.

## 3. AWS production architecture (M3.5)

```
Route53 → CloudFront ─→ S3 (web static build)
        → ALB ─→ ECS Fargate: core-api (2 tasks min, target-tracking autoscale)
RDS Postgres (Multi-AZ off at start, automated backups 7d, PITR on)
ElastiCache Redis (single node → replica when there are real users)
Secrets Manager → injected as env vars into ECS task definitions
CloudWatch: logs (JSON via task stdout — no agent needed, ECS's awslogs
            driver tails container stdout straight into a log group),
            a metric filter on ERROR-level JSON lines, alarms; SNS → email
core-api's own AlertingAppender → optional Slack/generic webhook (§6)
```

No sticky sessions, no WebSocket gateway — office-realtime never shipped, so the ALB is a plain HTTP target group.

Cost posture at launch: smallest Fargate sizes, single-AZ RDS `db.t4g.micro`, single Redis node. Scale on evidence, not anticipation.

**Provenance (M3.5, session 32): this architecture exists as real Terraform under `infra/terraform/` (`network.tf`/`ecs.tf`/`rds.tf`/`redis.tf`/`web.tf`/`secrets.tf`/`observability.tf`/`dns.tf`), written and internally consistent, but deliberately NOT applied this session** — there is no AWS account, no AWS CLI, and no credentials in this dev environment, and provisioning real cloud infrastructure (cost, a purchased domain, an external shared system) needs the owner's own AWS account and explicit go-ahead, not something to do unilaterally from a coding session. `terraform validate`/`fmt` were run against every file; `terraform plan`/`apply` were not. Whoever runs this for real supplies `terraform.tfvars` (domain name, AWS account/region, alert email) and needs an S3 backend bucket for state (documented in `infra/terraform/README.md`) before the first `apply`.

## 4. CI/CD (GitHub Actions)

**Provenance: both workflows below exist as real files (`.github/workflows/ci.yml`, `.github/workflows/deploy.yml`), written at M3.5, never run** — there's no ECR repo, no ECS cluster, and no repo secrets (`AWS_ROLE_ARN` etc.) for `deploy.yml` to actually use yet; it will start working the moment §3's Terraform is applied and the secrets are set, no workflow-file changes needed. `ci.yml` (PR pipeline) has no such dependency and is live from the moment this branch merges — every PR against `main` runs it for real.

1. **PR pipeline** (`ci.yml`, `make check`): lint + typecheck + unit + integration tests (Testcontainers). Blocks merge. **Live now.**
2. **Merge to main** (`deploy.yml`): build both images, push to ECR, auto-deploy to **staging**, run a smoke script (signup → hire → task → approve via API). Needs §3's ECR/ECS to exist first.
3. **Prod deploy:** manual approval `environment: production` gate on the same workflow (tag-triggered `release-*`). No direct pushes to prod.
4. **Migrations:** Flyway runs on core-api startup; migrations must be backward-compatible with the previous app version (expand → migrate → contract pattern) so deploys are zero-downtime.

## 5. Configuration & secrets
- All config via env vars (08 §Config). Secrets Manager in AWS; `.env` locally.
- LLM provider keys are **platform-level** at launch; per-company BYO-key is a backlog item (07 "Deferred — post-pilot backlog"). It was parked partly because it changes billing math — with billing itself deferred post-pilot (2026-07-17, 07 Rev D), that objection is void and this is now a plain isolation/key-handling call whenever a pilot company asks.
- **No payment configuration exists or is expected before M3.3** (deferred post-pilot): no Stripe keys, no webhook secret, no billing env vars. The prod deploy at M3.5 ships unmetered. `usage_records` remains the token-accounting truth regardless — that is metering for control, not charging.
- New at M3.5: `ATRIUM_RATE_LIMIT_*` (08 §Security rule 7), `ATRIUM_ALERT_WEBHOOK_URL` (§6 below). Neither is a secret in the Secrets-Manager sense (a webhook URL is closer to config), but both flow through the same env-var path as everything else.
- ~~`OFFICE_ROOM_TOKEN_SECRET`~~ — void, no Colyseus server ever existed to hold a room token.

## 6. Observability

**Provenance (M3.5, session 32): logging + alerting are real, running code in `core-api`, not just this doc's description of intent** — `logback-spring.xml` + `logstash-logback-encoder`, `common.AlertingAppender`, and MDC binding are all merged and covered by tests; only the AWS-side consumers (CloudWatch metric filter, SNS topic) live in the unapplied Terraform from §3.

- **Structured JSON logs.** `SPRING_PROFILES_ACTIVE=prod` (set by the ECS task definition, never locally) switches `logback-spring.xml` from Spring Boot's normal human-readable console pattern to `LogstashEncoder` — ECS's `awslogs` driver tails container stdout straight into CloudWatch Logs, so the JSON line IS the CloudWatch log event, no separate shipping agent. `companyId`/`userId`/`taskId`/`agentId` ride in SLF4J's MDC — `TenantContextFilter` binds `companyId`/`userId` for the lifetime of every authenticated request; `WorkBroker.claimNext`, `TaskEventRecorder.record`, and the `LlmLoopRuntime`/`EchoRuntime` poll loops bind `taskId`/`agentId` around the work they do. Local `docker compose up`/`mvn test` are unaffected — no profile is set, so the console stays exactly as readable as every prior session's `docker logs`/Flyway-log greps have relied on.
- **Error alerting.** `common.AlertingAppender` (a Logback `AppenderBase<ILoggingEvent>`) watches for `ERROR`-level events and POSTs a small JSON payload (`{text: "<logger> <level>: <message>"}`, Slack-incoming-webhook-shaped so a real Slack webhook URL works with zero adaptation, but generic enough for any JSON-accepting endpoint) to `ATRIUM_ALERT_WEBHOOK_URL` — unset (the local/test default) makes the appender a complete no-op, never an exception. A 30s global cooldown (configurable, `atrium.alerting.cooldown-seconds`) prevents one error storm from becoming a webhook storm; it deliberately drops alerts during cooldown rather than queuing them; the *count* of dropped errors during a cooldown window is still visible in the JSON logs themselves; a channel getting paged 500 times for one root cause is a worse failure mode than one delayed page, so the cooldown trades a small chance of a missed *second* distinct error during the window for guaranteed protection against alert storms. **This is the app-level half of "a triggered error alerts within 1 min"** — the AWS-level half (CloudWatch metric-filter alarm → SNS → email, §3) is real Terraform but fires only once real infra exists; both paths can point at the same downstream (SNS can itself post to a Slack webhook), so they're complementary, not redundant.
- Metrics (unbuilt this session, tracked as a real follow-on, not invented-and-abandoned): queue depth per skill, claim latency, lease expirations/min, LLM call latency + error rate, tokens/min per company. Actuator's `/actuator/metrics` (already exposed, M0.1) is the wiring point whenever this gets built — nothing here needed new infra to unblock it.
- Alarms (staging+prod, in the unapplied Terraform): core-api 5xx rate (ALB target group metric), the AlertingAppender's own ERROR-log metric filter, RDS storage. Reclaim-job/Redis-pub/sub-lag alarms are a genuine follow-on (need a custom CloudWatch metric core-api doesn't emit yet) — not built this session, not silently dropped either.
- Optional dev-only: Langfuse/Helicone tracing for prompt debugging (usage_records stays the billing truth).

## 7. Backup & recovery
- RDS automated backups + weekly manual snapshot before any risky migration.
- Redis is rebuildable (cache + transient events) — no backup needed.
- Runbook file `docs/notes/runbook.md`: restore-from-snapshot steps, rollback = redeploy previous image tag + (if needed) contract-phase migration revert. **Not written this session** — real content needs a real deployed environment to write accurately against; tracked as a M3.5 follow-on rather than a fabricated placeholder.

## 8. Domain, TLS, and naming
- Buy domain at Phase 3 start (product-name check is on the same checklist). ACM certs via DNS validation; CloudFront + ALB both TLS. **Not done this session** — no domain purchase happened; `infra/terraform/dns.tf` takes the domain as a `terraform.tfvars` input and is inert (no Route53 zone created) until one is supplied.
- Product name referenced only via `PRODUCT_NAME` env/config — one-line rename.

## 9. Deployment milestones mapping
- **M0.1:** docker-compose local complete.
- ~~M2.4c: staging environment stood up~~ — retired with the office track (2026-07-15); staging was never actually built under that slot.
- **M3.5 (session 32):** app-level hardening (rate limiting, structured JSON logs, error-alerting webhook, load test script) is real and merged. CI (`ci.yml`) is live. Deploy pipeline (`deploy.yml`), Terraform (§3), and staging/prod themselves are **written but not applied** — no AWS account/credentials exist in this environment; running them for real is the owner's own next action (`infra/terraform/README.md` has the exact steps). The literal "one full deploy from git tag to live URL with zero manual AWS-console steps" Done-when is therefore **not yet verified live** — everything upstream of "supply AWS credentials and run `terraform apply`" is done and tested.
