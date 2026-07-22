# Deployment

Atrium runs in two shapes: a **local** one-command Docker stack, and a **prod**
split across Render (backend) and Netlify (frontend). The same core-api Docker
image is used in both — only its environment differs.

## Local — run the whole app with one command

Requires only **Docker**. Nothing else to install.

```bash
git clone <repo> && cd Atrium
cp .env.example .env         # optional; add GOOGLE_API_KEY to see agents work
docker compose up            # add --build after pulling new changes
```

- Web:      http://localhost:5173
- API:      http://localhost:8080/api/v1
- Health:   http://localhost:8080/actuator/health

`docker compose up` starts Postgres (pgvector), Redis, core-api, and the web dev
server (hot reload). Migrations apply automatically on core-api boot. Without an
LLM key, agents park idle — that's the correct "not configured" state; add
`GOOGLE_API_KEY=...` to `.env` (free key at https://aistudio.google.com) to run
the full claim→work→approve loop at $0.

To sign up a demo company + agents: `docker compose exec web npm run seed:dev`.

## Prod — Render (backend) + Netlify (frontend)

Both platforms auto-deploy on push from their GitHub integration. Config lives in
the repo, so there's nothing to build by hand.

- **Backend** → [`render.yaml`](render.yaml): core-api (Docker) + managed Postgres
  (pgvector) + Key Value (Redis). Render only rebuilds on `core-api/**` changes.
- **Frontend** → [`netlify.toml`](netlify.toml): builds `web/`, SPA redirects,
  skips rebuilds on backend-only commits.

First-time setup and the URL cross-wiring (Render CORS ↔ Netlify API URL) are
documented at the top of each file. See also `infra/terraform/` for the
alternative AWS path (written, not applied).

## How the two environments differ

| Concern            | Local (`docker compose`)                 | Prod (Render + Netlify)                          |
| ------------------ | ---------------------------------------- | ------------------------------------------------ |
| Orchestration      | `docker-compose.yml`                     | `render.yaml` + `netlify.toml`                   |
| Backend            | core-api container (`core-api/Dockerfile`) | Render Docker web (**same** Dockerfile)         |
| Frontend           | Vite dev server (`web/Dockerfile.dev`, HMR) | Netlify static build (or `web/Dockerfile`)      |
| Postgres           | `pgvector` container                     | Render managed Postgres                          |
| Redis              | `redis` container                        | Render Key Value                                 |
| Spring profile     | default → human-readable logs            | `prod` → structured JSON logs                    |
| CORS origin        | `localhost:5173` (default)               | Netlify URL (`ATRIUM_CORS_ALLOWED_ORIGINS`)      |
| Web → API URL      | `localhost:8080` (default)               | Render URL (`VITE_API_BASE_URL`, build-time)     |
| JDBC URL           | full `DATABASE_URL` (compose)            | composed from `DATABASE_HOST/PORT/NAME`          |
| Secrets            | `.env` (optional)                        | Render / Netlify dashboards                      |
| JWT secret         | insecure dev fallback                    | `ATRIUM_JWT_SECRET` (Render-generated)           |

The frontend picks its target purely from build-time env: `VITE_USE_MOCKS=1`
runs a fully static mock demo (no backend), `VITE_USE_MOCKS=0` (the default) talks
to the real API at `VITE_API_BASE_URL`.
