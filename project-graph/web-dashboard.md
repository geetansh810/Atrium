# web-dashboard

**What:** React dashboard in `web/` — the non-game UI: sidebar, roster, task rails, bot bar, and all reference-2 panels.

**State (2026-07-13, M0.8): ON THE REAL API.** Every surface from `atrium-docs/assets/reference-2-full-ui-composite.png` is built and interactive. Agents/tasks/budgets now flow through [[frontend-shared]]'s `apiStore.tsx` (React Query over [[core-api]]) by default — `VITE_USE_MOCKS=1` still gets the original mock reducer. Chat/announcements/analytics panels stay mock-backed in both modes (no backend endpoint exists for them yet).

**Files (`web/src/dashboard/`):**
- `DashboardLayout.tsx` — composes shell + mounts the active panel over the office area
- `Sidebar.tsx` — rooms nav (camera shortcuts), Apps nav (with Approvals escalation badge), Who's Here roster (click → profile), Invite Agent
- `TopBar.tsx` — observe toggle, clock, derived online count, rail toggle
- (OfficePlaceholder is gone — [[web-office]]'s `OfficeCanvas` now mounts in `DashboardLayout`; room nav pans the office camera, roster/avatar clicks open the profile panel)
- `RightRail.tsx` — My Tasks (click → workspace, + → New Task) + Agent Status
- `BotBar.tsx` — bot notices/toasts, "Message Atrium" → bot DM with simulated reply, quick actions
- `panels/` — `PanelShell` chrome + `panels.css`, then: `AgentProfilePanel`, `WorkspacePanel` (tabs + subtask toggling; full checklist auto-submits to pending_review), `AnalyticsPanel`, `ChatPanel` (channels/DMs, simulated replies), `AnnouncementsPanel` (+compose), `ApprovalsPanel` (approve/reject/unblock), `BudgetPanel` (cap edit inline), `TaskFlowPanel`, `FocusPodPanel` (live timer, Exit Pod), `InviteAgentModal`, `NewTaskModal`

**Pending:** chat/announcements/analytics/escalations wiring once core-api grows those endpoints (M1.x/M2.3+). `agent.status_changed` isn't published by core-api yet, so the office view doesn't move live between manual status PATCHes.

**Contracts:** UI spec `atrium-docs/01-product-spec.md §3–4` · API shapes `04-api-contract.md`.

**M0.8 additions (`web/src/shared/`):** `api.ts` (typed fetch client), `adapters.ts` (DTO → mock-shaped types), `queries.ts` (React Query hooks, incl. `useEnrichedTasks` merging list+detail+events), `apiStore.tsx`/`mockStore.tsx` (the two `store.tsx` providers), `config.ts` (`VITE_USE_MOCKS`/`VITE_COMPANY_ID`), `roleTemplateDefaults.ts` (the 3 real seeded templates). `web/scripts/seed-dev.mjs` bootstraps a demo company.

Links: [[_Atrium]] · [[frontend-shared]] · [[web-office]] · [[core-api]]
