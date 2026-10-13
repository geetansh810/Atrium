# 9 · Run & deploy

The authoritative short version is the root [`README.md`](../../README.md) and [`DEPLOYMENT.md`](../../DEPLOYMENT.md); this page adds the "why" and the checks.

## Run the whole stack (Docker only)

```bash
cp .env.example .env          # optional; set GOOGLE_API_KEY to let agents do real work
docker compose up --build     # --build matters: plain `up` reuses a stale image
```

| URL | What |
|---|---|
| http://localhost:5173 | Web dashboard (Vite dev server, hot reload) |
| http://localhost:8080/api/v1 | core-api |
| http://localhost:8080/actuator/health | `{"status":"UP"}` when ready |

[`docker-compose.yml`](../../docker-compose.yml) starts `pgvector/pgvector:pg16`, `redis:7`, `core-api` (built from [`core-api/Dockerfile`](../../core-api/Dockerfile)) and the `web` dev server. Flyway applies every migration on core-api startup.

**Get in:** sign up at `:5173` (a fresh signup runs the onboarding wizard), or `docker compose exec web npm run seed:dev` to create a demo company with agents and print its login. Note the seeded admin from `V15` ([08](08-security-tenancy.md#known-gaps-do-not-assume-these-are-covered)).

**Make an agent actually work:** put a key in the root `.env` (Compose loads `.env`, *not* `.env.local`) and `docker compose up -d --build core-api`.

| Key | Enables | Cost |
|---|---|---|
| `GOOGLE_API_KEY` | Gemini agents (the seeded demo `GeminiCoder`) — free tier at aistudio.google.com | $0 |
| `ANTHROPIC_API_KEY` | Claude agents (the `coder`/`tester`/`research` template defaults) | paid |
| `OPENAI_API_KEY` | Embeddings for semantic memory/knowledge recall | paid, small |

With no keys everything except agent work runs; agents *park* (log "provider … not configured") rather than fail. Missing the embeddings key only disables semantic recall.

**Mock mode** (no backend): `VITE_USE_MOCKS=1 npm run dev` in `web/`, or the web container with that env.

## Run without Docker for the web

```bash
cd web && npm install
PATH="/opt/homebrew/bin:$PATH" npm run dev   # macOS/Homebrew note: the lint/vite toolchain needs a recent Node
```

Requires a Node version satisfying the toolchain (≥ 20.19 or 22). The backend needs JDK 21 and Docker for Testcontainers.

## Checks

| Command | Covers |
|---|---|
| `make check` | web typecheck + lint, core-api `verify -DskipTests` |
| `cd core-api && ./mvnw test` | Backend suite; **needs Docker** (Testcontainers Postgres + Redis) |
| `cd web && npx tsc -b && npm run lint` | Frontend only |
| `BASE_URL=http://localhost:8080/api/v1 ./.github/scripts/smoke-test.sh` | Signup → hire → task → poll for `pending_review` → approve, against a running stack |
| [`loadtest/atrium-load-test.js`](../../loadtest/atrium-load-test.js) via k6 | Realistic early concurrency |

Test conventions: integration tests extend [`IntegrationTestBase`](../../core-api/src/test/java/app/atrium/IntegrationTestBase.java) (one shared pgvector + Redis container set), drive agent loops by calling `runOnce` rather than racing the 15s timer, and use `adminJdbc()` for fixtures that need cross-tenant SQL. Known flake: `LearningPipelineTest` is unreliable in the full suite and has been seen failing alone — rerun before assuming you broke it.

## Configuration reference

All keys, with comments, in [`.env.example`](../../.env.example) and [`application.yml`](../../core-api/src/main/resources/application.yml). The ones people reach for:

| Variable | Default | Notes |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | compose values | Flyway's (bootstrap) connection |
| `ATRIUM_APP_DB_USER` / `ATRIUM_APP_DB_PASSWORD` | `atrium_app` | The RLS-enforced runtime connection; override in shared envs |
| `REDIS_URL` | `redis://localhost:6379` | Relay + rate limits |
| `ATRIUM_JWT_SECRET` | insecure dev fallback | **Must** be a real ≥32-byte secret outside local |
| `ATRIUM_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | The web origin |
| `ATRIUM_LLM_TIMEOUT` | `120s` | Per call |
| `ATRIUM_LLM_LOG_RETENTION_DAYS` | `30` | LLM request-log purge |
| `ATRIUM_MEMORY_TTL_DAYS` | `90` | Lesson/summary archive |
| `ATRIUM_RATE_LIMIT_*` | 300 / 10 / 240 per min | Company / auth-by-IP / worker |
| `SPRING_PROFILES_ACTIVE=prod` | unset | JSON logs; set only in deployed envs |

## Deployed shapes

| Shape | Status | Where |
|---|---|---|
| **Local compose** | Daily use | above |
| **Render (API + Postgres + Redis) + Netlify (web)** | Config in repo; same core-api image, `prod` profile | [`render.yaml`](../../render.yaml), [`netlify.toml`](../../netlify.toml), [`DEPLOYMENT.md`](../../DEPLOYMENT.md) |
| **AWS (ALB → ECS Fargate → RDS, ElastiCache, S3+CloudFront)** | Terraform written and validated; **never applied** | [`infra/terraform/`](../../infra/terraform/), [`10-deployment.md`](../10-deployment.md) |

CI ([`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)) runs web typecheck/lint and backend `verify` + tests on every push; [`deploy.yml`](../../.github/workflows/deploy.yml) is the AWS pipeline and is inert until the Terraform is applied and repo secrets exist.

## When something looks wrong

1. `docker compose logs -f core-api` — startup, "parked — provider not configured", per-agent tick errors.
2. `/actuator/health` and `/actuator/metrics` (`atrium.relay.lag`, `atrium.relay.pending`).
3. **LLM Logs** page for the exact prompt/response of a failed call.
4. `GET /tasks/{id}/events` for what the task did.

*Verified against:* [`docker-compose.yml`](../../docker-compose.yml), [`Makefile`](../../Makefile), [`.env.example`](../../.env.example), [`.github/workflows/`](../../.github/workflows/), [`DEPLOYMENT.md`](../../DEPLOYMENT.md).
