# _Atrium (hub)

Multi-tenant SaaS: companies hire AI agents as employees — roles, skills, hierarchy, token-budget payroll, approval gates, and a live Team view where each agent's zone is derived from live task state.

**Rule for sessions:** open only the nodes your task touches. Each node says which `atrium-docs/` sections to load for full contracts.

## Services
- [[web-dashboard]] — React dashboard, redesigned into an "AI Operating System" (MF-1–6 ALL DONE: router + new shell + primitives + real Mission Control + Tasks kanban/drawer/review inbox + Employees/profile/Organization + Projects/Knowledge/Workflow/Reports + Notifications/Cmd+K/Settings + contract cleanup — zero legacy panels remain; Reports' Agent Performance leaderboard + 7d/top-skills and Organization's period-scoped Budget Ledger now read real M2.3 analytics in API mode; M2.6 fixed Agent Profile's Activity/Performance/Memory tabs, which had drifted stale behind M2.3/M-MEM1/M-LN1; M-LN2 added a Memories review-queue tab to Review Inbox, closing the learning-surface UI series — zero backend touched by either). ON REAL API, mocks still available behind `VITE_USE_MOCKS=1`
- [[web-office]] / [[office-realtime]] — RETIRED 2026-07-15: the SkyOffice office track (client fork + planned Colyseus server) was removed, replaced by the live Team view (`/team`) in [[web-dashboard]]
- [[core-api]] — Spring Boot source of truth (M0.1–M0.8 + M-SK1 + M-CTX1 + M-MEM1 + M-LN1 + M-KN1 + M1.1 + M1.2 + M2.1 + M2.2 + M2.3 done: scaffold, migrations, registry, routing + outbox + claim/lease, LLM provider SPI + Anthropic/Google, agent runtime loop, approve/reject + rejection-feedback rework, real budget caps + soft-alert + auto-pause, outbox→Redis relay for office/dashboard, CORS enabled for the browser dashboard, skills registry live, skills-into-prompts ContextAssembler live, memory store + recall live, learning pipeline + review governance live, knowledge ingestion live, first Phase-1 business role — a real "Agrawal Namkeen" pilot company/role/agent — proven through the pipeline with zero core code changes; Phase 1 closed — a real pilot task was created and reviewed via the dashboard UI, unassisted; Phase 2 underway — two more roles proven live, task decomposition + Task Flow API live, then budget ledger + analytics rollups live: a second durable outbox consumer (`StatsRollupWorker`) plus a nightly drift-correcting reconciliation job feed `agent_stats_daily`, and 5 analytics endpoints answer "what did this agent cost"; M2.5 added the new [[communication]] module — channels/messages/announcements (V7) + a third durable consumer `ChatNoticePipeline` turning `task.completed` into a bot notice in `#general`, plus `GET /escalations` — verified live end-to-end; M-LN2-fix (session 27) closed a real embeddings-degrade bug so the learning pipeline writes memories without an `OPENAI_API_KEY`; M-AR1 (session 28) proved the `AgentRuntime` axis is genuinely pluggable — a second runtime, `EchoRuntime`, landed with zero diff to routing/registry/migrations, closing every card on the 17-backend-execution-plan list)

## core-api modules
[[registry]] · [[routing]] · [[execution]] · [[accountability]] · [[communication]] · [[realtimebridge]] · [[agentmind]]

## Cross-cutting
- [[agent-platform]] — Rev-C backend design: pluggable LLMs/runtimes, skills+memory+learning, outbox event backbone (RELAY LIVE at M0.75; skills registry LIVE at M-SK1; skills-into-prompts ContextAssembler LIVE at M-CTX1; memory store + recall LIVE at M-MEM1; learning pipeline + review governance LIVE at M-LN1; knowledge ingestion LIVE at M-KN1 — the full depth series is now complete, docs 12–17)
- [[data-model]] — Postgres schema, the contract everything mirrors
- [[realtime-events]] — Redis pub/sub bridge out of core-api (relay live, currently no subscriber — the office consumer was retired 2026-07-15)
- [[milestones]] — 26-milestone plan + what's actually done
- [[frontend-shared]] — theme tokens, TS types, mock fixtures

## Ground rules (full list: root CLAUDE.md)
Tenant isolation everywhere · SKIP LOCKED claim + lease + idempotency keys · append-only task_events · pending_review hard gate · roles are data, never router code · secrets never meet prompts · Team view is pure derivation over agents/tasks.
