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
| `docker-compose.yml` | Local full-stack (Postgres + Redis + core-api + web) |
| `render.yaml` / `netlify.toml` | Prod deploy config — backend on Render, frontend on Netlify |
| `DEPLOYMENT.md` | Local vs prod: how to run and how the two environments differ |

## Run locally

**The only prerequisite is Docker.** Nothing else to install — no JDK, Maven, or
Node needed. If you fork or clone this repo, one command brings up the entire app
(Postgres with pgvector, Redis, the Spring Boot backend, and the web frontend):

```bash
# Optional: a free Google Gemini key (https://aistudio.google.com, no card)
# lets agents actually run work. Without any LLM key they park idle — that's
# the correct "not configured" state, everything else still works.
cp .env.example .env      # then set GOOGLE_API_KEY= if you want a working agent

docker compose up         # add --build after pulling new changes
```

Then open:

- **Web:**    http://localhost:5173
- **API:**    http://localhost:8080/api/v1
- **Health:** http://localhost:8080/actuator/health → `{"status":"UP"}`

Flyway applies every migration automatically on `core-api` startup — no manual DB
setup. First run builds the images, so give it a few minutes.

Sign up through the web UI at http://localhost:5173, **or** seed a demo company +
agents from the command line:

```bash
docker compose exec web npm run seed:dev   # prints login credentials — save that output
```

If it doesn't come up clean: `docker compose logs -f core-api` to watch startup,
and make sure the Docker daemon (Docker Desktop) is actually running first.

### Mock mode — frontend only, no backend

To demo the dashboard off local JSON fixtures with no backend at all, run the web
container with mocks on (or `VITE_USE_MOCKS=1 npm run dev` if you have Node):

```bash
docker compose run --rm --no-deps -e VITE_USE_MOCKS=1 -p 5199:5173 web
# → http://localhost:5199   (--no-deps = don't start the backend)
```

## Deploy

Prod runs split: **backend on Render**, **frontend on Netlify** — both auto-deploy
on push from their GitHub integration, with all build config committed
([`render.yaml`](render.yaml), [`netlify.toml`](netlify.toml)). The same core-api
Docker image is used locally and in prod; only its environment differs. Full setup
steps and an environment-by-environment comparison are in
[`DEPLOYMENT.md`](DEPLOYMENT.md).

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
