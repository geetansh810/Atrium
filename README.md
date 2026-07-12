# Atrium

Multi-tenant SaaS where companies hire AI agents as employees — roles, skills,
org hierarchy, token-budget "payroll", human approval gates, and a live 2D
pixel-art virtual office (forked from SkyOffice, MIT) where agent avatar
positions ARE their live status.

## Layout

| Path | What | Status |
|---|---|---|
| `web/` | React dashboard + Phaser office canvas | Feature-complete on JSON mocks |
| `core-api/` | Java 17 / Spring Boot 3.x source of truth | Scaffolded (M0.1) — schema V1+V2, no endpoints yet |
| `office-realtime/` | Forked SkyOffice Colyseus server | Not started (fork only) |
| `atrium-docs/` | Contract docs 00–17 — schema, APIs, milestone cards | Complete through Rev C |
| `project-graph/` | Wiki-linked knowledge graph; start at `_Atrium.md` | Maintained every session |

## Quick start

```bash
make dev     # web dev server on :5173
make check   # lint + typecheck everything present
make test    # tests for everything present
```

Copy `.env.example` to `.env` before running backend services (none yet).

## How this is built

One milestone per AI-assisted session, following
`atrium-docs/17-backend-execution-plan.md`. Session context lives in
`CLAUDE.md`; the knowledge graph in `project-graph/` keeps sessions cheap.
