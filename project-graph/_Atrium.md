# _Atrium (hub)

Multi-tenant SaaS: companies hire AI agents as employees — roles, skills, hierarchy, token-budget payroll, approval gates, and a live pixel-art office where avatar position = agent status.

**Rule for sessions:** open only the nodes your task touches. Each node says which `atrium-docs/` sections to load for full contracts.

## Services
- [[web-dashboard]] — React dashboard, redesigned into an "AI Operating System" (MF-1–6 ALL DONE: router + new shell + primitives + real Mission Control + Tasks kanban/drawer/review inbox + Employees/profile/Organization + Projects/Knowledge/Workflow/Reports + Notifications/Cmd+K/Settings + contract cleanup — zero legacy panels remain). ON REAL API, mocks still available behind `VITE_USE_MOCKS=1`
- [[web-office]] — SkyOffice Phaser client fork, BUILT mock-driven since session 3, mounted at `/office` (one optional route since MF-1, not the primary UI)
- [[core-api]] — Spring Boot source of truth (M0.1–M0.8 + M-SK1 + M-CTX1 + M-MEM1 + M-LN1 done: scaffold, migrations, registry, routing + outbox + claim/lease, LLM provider SPI + Anthropic/Google, agent runtime loop, approve/reject + rejection-feedback rework, real budget caps + soft-alert + auto-pause, outbox→Redis relay for office/dashboard, CORS enabled for the browser dashboard, skills registry live, skills-into-prompts ContextAssembler live, memory store + recall live, learning pipeline + review governance live)
- [[office-realtime]] — Colyseus presence server (NOT STARTED, fork only)

## core-api modules
[[registry]] · [[routing]] · [[execution]] · [[accountability]] · [[realtimebridge]] · [[agentmind]]

## Cross-cutting
- [[agent-platform]] — Rev-C backend design: pluggable LLMs/runtimes, skills+memory+learning, outbox event backbone (RELAY LIVE at M0.75; skills registry LIVE at M-SK1; skills-into-prompts ContextAssembler LIVE at M-CTX1; memory store + recall LIVE at M-MEM1; learning pipeline + review governance LIVE at M-LN1; knowledge ingestion (M-KN1) is the one remaining depth-series piece, docs 12–17)
- [[data-model]] — Postgres schema, the contract everything mirrors
- [[realtime-events]] — Redis pub/sub bridge, core-api → office
- [[milestones]] — 26-milestone plan + what's actually done
- [[frontend-shared]] — theme tokens, TS types, mock fixtures

## Ground rules (full list: root CLAUDE.md)
Tenant isolation everywhere · SKIP LOCKED claim + lease + idempotency keys · append-only task_events · pending_review hard gate · roles are data, never router code · secrets never meet prompts · office is a projection.
