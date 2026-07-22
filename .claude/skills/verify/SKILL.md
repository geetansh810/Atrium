---
name: verify
description: Verify an Atrium change end-to-end — which checks to run for backend (core-api), frontend (web), or full-stack changes, and how to drive the running app with dev auth headers.
---

# Verifying changes in Atrium

Pick the row(s) matching what changed. "Green build" alone is never verification —
drive the affected flow.

| Changed | Minimum proof |
|---|---|
| core-api code | `cd core-api && ./mvnw test` (Docker running) — includes Testcontainers ITs |
| SQL migration | `./mvnw test` (fresh Testcontainers DB applies it) AND `docker compose up` boots clean |
| web/ code | `cd web && npx tsc -b && npm run lint`, then browser-verify via the `web-dev` launch config (port 5173) |
| anything | `make check` from repo root before calling it done |
| LLM provider code | WireMock suite runs always; live: `ANTHROPIC_API_KEY=… ./mvnw test -Dtest=AnthropicLiveTest` |

## Driving the real backend

```bash
docker compose up -d          # pgvector:5432, redis:6379, core-api:8080
curl -s localhost:8080/actuator/health          # {"status":"UP"}

# Bootstrap (the ONE /api route without a tenant header)
COMPANY=$(curl -s -X POST localhost:8080/api/v1/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"Dev Co","slug":"dev-co-'$RANDOM'"}' | jq -r .id)

H="X-Company-Id: $COMPANY"    # every other /api call needs this

# Hire (seeded templates: coder | tester | research)
AGENT=$(curl -s -X POST localhost:8080/api/v1/companies/$COMPANY/agents -H "$H" \
  -H 'Content-Type: application/json' \
  -d '{"name":"CoderAgent","roleTemplateKey":"coder","roleTitle":"Engineer","skillTags":["coding"],"modelProvider":"anthropic","modelName":"claude-sonnet-5"}' | jq -r .id)

# Task (requiredSkill must exist on the roster)
TASK=$(curl -s -X POST localhost:8080/api/v1/companies/$COMPANY/tasks -H "$H" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Try it","requiredSkill":"coding"}' | jq -r .task.id)

# Worker API (agent auth = X-Agent-Id; claim 409 = held, never retry)
curl -s -X POST localhost:8080/api/v1/tasks/$TASK/claim -H "$H" -H "X-Agent-Id: $AGENT"
curl -s "localhost:8080/api/v1/tasks/$TASK/events" -H "$H" | jq .
```

Optional user actor: add `-H "X-User-Id: <uuid>"` — the uuid MUST exist in `users`
(FK) or creates 500; insert via psql first.

DB poke: `docker compose exec postgres psql -U atrium -d atrium -c 'SELECT …'`
(task_events is append-only; outbox_events.published_at NULL = pending relay).

## Reading test results

- Failure detail lives in `core-api/target/surefire-reports/TEST-*.xml` — grep for `Caused by`.
- One Spring context is shared across all ITs (same `IntegrationTestBase` config) —
  adding `@TestPropertySource`/`@MockBean` to one test forks a second context and doubles boot
  time; put shared config on the base class instead.
- Container startup is skipped when Testcontainers reuse is enabled (see
  `~/.testcontainers.properties`: `testcontainers.reuse.enable=true`) — reused DBs keep data
  between runs, so tests must keep using random slugs/ids, never fixed fixtures.
