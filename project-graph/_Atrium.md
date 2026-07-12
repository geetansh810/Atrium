# _Atrium (hub)

Multi-tenant SaaS: companies hire AI agents as employees — roles, skills, hierarchy, token-budget payroll, approval gates, and a live pixel-art office where avatar position = agent status.

**Rule for sessions:** open only the nodes your task touches. Each node says which `atrium-docs/` sections to load for full contracts.

## Services
- [[web-dashboard]] — React dashboard (BUILT, mock data)
- [[web-office]] — future SkyOffice Phaser client (NOT STARTED, fork only)
- [[core-api]] — Spring Boot source of truth (SCAFFOLDED — M0.1 done: modules, common/, V1+V2 migrations, compose, smoke test)
- [[office-realtime]] — Colyseus presence server (NOT STARTED, fork only)

## core-api modules
[[registry]] · [[routing]] · [[execution]] · [[accountability]] · [[realtimebridge]]

## Cross-cutting
- [[agent-platform]] — Rev-C backend design: pluggable LLMs/runtimes, skills+memory+learning, outbox event backbone (SPEC'D, docs 12–17)
- [[data-model]] — Postgres schema, the contract everything mirrors
- [[realtime-events]] — Redis pub/sub bridge, core-api → office
- [[milestones]] — 26-milestone plan + what's actually done
- [[frontend-shared]] — theme tokens, TS types, mock fixtures

## Ground rules (full list: root CLAUDE.md)
Tenant isolation everywhere · SKIP LOCKED claim + lease + idempotency keys · append-only task_events · pending_review hard gate · roles are data, never router code · secrets never meet prompts · office is a projection.
