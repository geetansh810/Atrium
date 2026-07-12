# 10 — Deployment Plan

## 1. Environments

| Env | Purpose | Infra | When |
|---|---|---|---|
| **local** | daily development | docker-compose (postgres, redis, core-api, office-realtime, web) | M0.1 onward |
| **staging** | pre-release verification, pilot users | single small AWS setup, same shape as prod | from M2.4c (office demo) |
| **prod** | real users | AWS, per below | M3.5 |

Rule: staging and prod differ only in size and secrets — never in shape. Everything is a container.

## 2. Container images
- `atrium/core-api` — multi-stage Dockerfile: maven build → JRE 17 slim runtime.
- `atrium/office-realtime` — node:20-slim, builds the Colyseus server.
- `atrium/web` — static build (Vite) served by nginx; office assets included.
- Tags: `m<milestone>-<shortsha>` + `latest-staging` / `latest-prod`. Never deploy `:latest` blind.

## 3. AWS production architecture (Phase 3 / M3.5)

```
Route53 → CloudFront ─→ S3 (web static build)
        → ALB ─→ ECS Fargate: core-api (2 tasks min, target-tracking autoscale)
               └→ ECS Fargate: office-realtime (sticky sessions ON — Colyseus
                  WebSockets require ALB stickiness; scale vertically first)
RDS Postgres (Multi-AZ off at start, automated backups 7d, PITR on)
ElastiCache Redis (single node → replica when there are real users)
Secrets Manager → injected as env vars into ECS task definitions
CloudWatch: logs (JSON), alarms; SNS → email/Slack for alerts
```

Cost posture at launch: smallest Fargate sizes, single-AZ RDS `db.t4g.micro`, single Redis node. Scale on evidence, not anticipation.

## 4. CI/CD (GitHub Actions)
1. **PR pipeline** (`make check`): lint + typecheck + unit + integration tests (Testcontainers). Blocks merge.
2. **Merge to main:** build all three images, push to ECR, auto-deploy to **staging**, run a smoke script (signup → hire → task → approve via API).
3. **Prod deploy:** manual approval step on the same pipeline (tag-triggered `release-*`). No direct pushes to prod.
4. **Migrations:** Flyway runs on core-api startup; migrations must be backward-compatible with the previous app version (expand → migrate → contract pattern) so deploys are zero-downtime.

## 5. Configuration & secrets
- All config via env vars (08 §Config). Secrets Manager in AWS; `.env` locally.
- LLM provider keys are **platform-level** at launch; per-company BYO-key is a Phase-3+ backlog item (changes billing math — decide before building).
- `OFFICE_ROOM_TOKEN_SECRET` rotated on schedule; rotation must not kick live rooms (tokens are join-time only).

## 6. Observability
- Structured JSON logs with `companyId`, `taskId`, `agentId` on every business log line.
- Metrics: queue depth per skill, claim latency, lease expirations/min, LLM call latency + error rate, tokens/min per company, WebSocket connections per room.
- Alarms (staging+prod): core-api 5xx rate, reclaim-job failures, Redis pub/sub lag, budget-guard errors, RDS storage.
- Optional dev-only: Langfuse/Helicone tracing for prompt debugging (usage_records stays the billing truth).

## 7. Backup & recovery
- RDS automated backups + weekly manual snapshot before any risky migration.
- Redis is rebuildable (cache + transient events) — no backup needed; office-realtime rebuilds from `/office-state`.
- Runbook file `docs/notes/runbook.md`: restore-from-snapshot steps, rollback = redeploy previous image tag + (if needed) contract-phase migration revert.

## 8. Domain, TLS, and naming
- Buy domain at Phase 3 start (product-name check is on the same checklist). ACM certs via DNS validation; CloudFront + ALB both TLS.
- Product name referenced only via `PRODUCT_NAME` env/config — one-line rename.

## 9. Deployment milestones mapping
- **M0.1:** docker-compose local complete.
- **M2.4c:** staging environment stood up (manual, scripted in `infra/staging.md`).
- **M3.5:** full prod per §3 with CI/CD §4, alarms §6, runbook §7. "Done when" for M3.5 includes: one full deploy from git tag to live URL with zero manual AWS-console steps.
