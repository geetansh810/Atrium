# web-dashboard

**What:** React dashboard in `web/` — the non-game UI: sidebar, roster, task rails, bot bar, and all reference-2 panels.

**State (2026-07-12): COMPLETE (mock-data v2).** Every surface from `atrium-docs/assets/reference-2-full-ui-composite.png` is built and interactive. All data flows through [[frontend-shared]]'s store — no API calls yet.

**Files (`web/src/dashboard/`):**
- `DashboardLayout.tsx` — composes shell + mounts the active panel over the office area
- `Sidebar.tsx` — rooms nav (camera shortcuts), Apps nav (with Approvals escalation badge), Who's Here roster (click → profile), Invite Agent
- `TopBar.tsx` — observe toggle, clock, derived online count, rail toggle
- (OfficePlaceholder is gone — [[web-office]]'s `OfficeCanvas` now mounts in `DashboardLayout`; room nav pans the office camera, roster/avatar clicks open the profile panel)
- `RightRail.tsx` — My Tasks (click → workspace, + → New Task) + Agent Status
- `BotBar.tsx` — bot notices/toasts, "Message Atrium" → bot DM with simulated reply, quick actions
- `panels/` — `PanelShell` chrome + `panels.css`, then: `AgentProfilePanel`, `WorkspacePanel` (tabs + subtask toggling; full checklist auto-submits to pending_review), `AnalyticsPanel`, `ChatPanel` (channels/DMs, simulated replies), `AnnouncementsPanel` (+compose), `ApprovalsPanel` (approve/reject/unblock), `BudgetPanel` (cap edit inline), `TaskFlowPanel`, `FocusPodPanel` (live timer, Exit Pod), `InviteAgentModal`, `NewTaskModal`

**Pending:** React Query wiring to [[core-api]] once it exists (replace store actions with mutations).

**Contracts:** UI spec `atrium-docs/01-product-spec.md §3–4` · API shapes `04-api-contract.md`.

Links: [[_Atrium]] · [[frontend-shared]] · [[web-office]] · [[core-api]]
