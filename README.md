# Atrium

Multi-tenant SaaS where companies hire AI agents as employees: roles, skills,
org hierarchy, token-budget "payroll", human approval gates, and a live Team
view where every agent's status is derived from real task state.

## Layout

| Path | What |
|---|---|
| `web/` | React dashboard (Vite, TypeScript, React Query) |
| `core-api/` | Java 21 / Spring Boot 3.x source of truth — registry, routing, execution, accountability, communication, agentmind |
| `atrium-docs/` | Contract docs 00–17 — schema, APIs, milestone cards, `compliance/` |
| `project-graph/` | Wiki-linked knowledge graph; start at `_Atrium.md` |
| `infra/terraform/` | AWS deploy (written and validated, not yet applied to a live account) |
| `loadtest/` | k6 load test |

## Run locally

Prerequisites: **Docker** (Compose) and **Node ≥20.19 or ≥22** for the web dashboard. No local JDK/Maven needed to run the app — `core-api` builds and runs entirely inside its own Docker image.

```bash
# 1. Env vars — the defaults work out of the box; a free Google Gemini key
#    (https://aistudio.google.com, no card needed) lets agents actually do work.
cp .env.example .env
# edit .env and set GOOGLE_API_KEY= if you want a working agent

# 2. Backend: Postgres (pgvector) + Redis + core-api on :8080.
#    --build matters — a plain `up -d` can reuse a stale image.
docker compose up -d --build

# check it actually came up before moving on
docker compose ps                        # all 3 containers should show Up (postgres/redis "healthy")
curl http://localhost:8080/actuator/health   # {"status":"UP"}

# 3. Frontend
cd web
npm install
npm run seed:dev     # signs up a demo company, hires 3 agents, prints login credentials — SAVE THAT OUTPUT, it's the only place they're shown
npm run dev           # dev server on :5173 — log in at / with the printed email/password
```

Flyway applies every migration automatically on `core-api` startup — no manual DB setup.

If step 2 doesn't come up clean: `docker compose logs -f core-api` to watch startup, and confirm the Docker daemon (Docker Desktop) is actually running before anything else — `docker compose up` fails immediately if it isn't.

### Mock mode — no backend needed

The dashboard can also run entirely off local JSON fixtures, with no Docker/API required:

```bash
cd web
npm install
VITE_USE_MOCKS=1 npm run dev -- --port 5199
```

### Everyday commands

```bash
make dev     # web dev server on :5173 (mocks off — needs the backend running)
make check   # web typecheck + lint, core-api verify (no tests)
make test    # web tests (none yet) + full core-api test suite (needs Docker — Testcontainers)

cd core-api && ./mvnw test          # backend tests only (needs Docker)
cd web && npx tsc -b && npm run lint  # frontend typecheck + lint only
```

If `npm run dev`/`npx tsc -b`/`npm run lint` fail with a Node-version error, a newer Node than the system default may be needed — retry with `PATH="/opt/homebrew/bin:$PATH"` prefixed if a working install exists there (see `CLAUDE.md`'s Commands section).

## How this is built

One milestone per AI-assisted session. Session context lives in `CLAUDE.md`
("Current state" / "Likely next"); the knowledge graph in `project-graph/`
keeps sessions cheap by loading only the docs a given task touches.
