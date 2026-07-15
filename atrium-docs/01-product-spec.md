# 01 — Product Specification
> **Office track removed 2026-07-15** — the SkyOffice virtual office (M2.4a–c, office-realtime, office_layout, /office-state) was retired and replaced by the live Team view (`/team`) in the dashboard; see `07-milestones.md`. Office references below are historical.

## 1. Vision

Atrium lets any company run a workforce of specialized AI agents the way it runs human staff: hired into roles, organized under managers, given budgets, held to approval gates, and visible at a glance in a live virtual office. Agents are deep specialists (scoped prompts, scoped knowledge, scoped tools per role) — not one generalist with different name tags.

## 2. Personas

- **Owner/Admin (primary):** signs up the company, hires agents, sets budgets, approves work. May be non-technical.
- **Member:** assigns tasks, chats with agents, reviews output within their permission.
- **Payroll manager (role or human):** watches the budget ledger, adjusts caps.
- **The agents themselves (system actors):** claim tasks, work, escalate, report.

## 3. Feature inventory (complete)

### 3.1 Company & roster
- Company registration; roster of agents with: name, avatar sprite, role title, skill tags, model provider+name, manager (agent or human), status, joined date.
- Hire via **Invite Agent** flow: pick a role template or define custom (role, skills, model, budget, manager).
- Statuses (exact set, from reference images): `online`, `working`, `in_meeting`, `in_focus`, `away`, `offline` — plus derived `flagged` (waiting on human).

### 3.2 Tasks & workflow
- Create task: title, description, required_skill, priority, ETA estimate; optional parent task.
- Subtask checklists with per-item done state (reference: "Analyze Q2 Data — Subtasks 3/5").
- Progress % per task (derived from subtasks or agent-reported), ETA countdown.
- Lifecycle: `queued → claimed → in_progress → pending_review → approved | rejected` (+ `flagged`, `cancelled`). Rejected returns to `in_progress` with feedback attached.
- Task Flow view: a graph of parent → children → completion (reference: User Request → DataAnalyst → fan-out → Report Generated).
- My Workspace: tabs **My Tasks / Assigned to Me / Completed**; task detail with subtasks, progress, ETA.

### 3.3 Virtual office (signature surface)
- Top-down pixel-art office, forked from SkyOffice. Rooms (exact set): **Lobby, Workstations, Meeting Room Alpha, Cafe, Focus Pods (×3+), Server Room, Rooftop**. Signage: "Atrium — Build. Automate. Scale.", FOCUS/BUILD/SHIP board, Announcements board on the floor.
- User walks as an avatar (WASD/arrows, E to sit — inherited from SkyOffice). Agents are server-driven avatars.
- **Position = state:** working → at an assigned desk with an activity speech bubble (✉ email, </> code, 📊 chart); in_meeting → Meeting Room; in_focus → a Focus Pod (with DND timer); away → Cafe/Rooftop; flagged → help-desk/lobby position.
- Click an agent → Agent Profile panel. Proximity/text chat with dialog bubbles (inherited). Webcam/screen-share features from SkyOffice are **stripped** in v1.
- Left nav room list doubles as camera shortcuts. "Who's Here (n)" live list with status dots.

### 3.4 Agent Profile (panel)
- Header: pixel avatar, name, live status, role title, joined date.
- Stats: **Tasks Completed** (count), **Success Rate** (%), **Focus Time** (duration).
- About text; skill chips (e.g. Web Search, Data Extraction, Summarization, Analysis).
- Current Tasks with per-task progress bar + ETA.
- Activity Feed: timestamped events (completed task X, started task Y, found N sources, joined Meeting Room Alpha) — rendered directly from `task_events` + presence events.

### 3.5 Chat & communication
- Channels (#general, #announcements, plus custom) and DMs with any agent.
- Atrium Bot: system narrator — greeting ("Welcome back… you have 4 tasks in progress"), daily digest, event notifications.
- Agents post human-readable status updates ("Working on the new API endpoint. ETA 30 minutes.").
- In-office speech bubbles mirror the latest chat/status line.

### 3.6 Analytics
- KPI cards: Total Agents (+delta), Tasks Completed (+%), Avg Success Rate (+delta), Focus Time today (+delta).
- Tasks Completed 7-day bar chart; Agent Performance leaderboard (success-rate bars); Top Skills Used chips with % share.

### 3.7 Budgets ("payroll")
- Per-company and per-agent monthly caps in tokens; spend metered per LLM call and rolled up per task/agent/period.
- Claim-time budget check; over-cap = blocked + flagged, never silent.
- Ledger UI: spend vs. cap bars, historical periods, cost-per-task drilldown. Phase 3: Stripe metered billing on top.

### 3.8 Announcements
- Company-wide announcements with category chips (Company / Update / Maintenance), shown in the panel and mirrored on the in-office board.

### 3.9 Approvals & escalation
- Pending-review queue; approve/reject with feedback; a two-clicks-from-login escalation surface listing every flagged item.

## 4. UI layout spec (from reference images — check against `assets/`)

**Main office screen (`reference-1-office-main-view.png`):**
- Left sidebar (~250px, dark navy): logo "ATRIUM / Agentic Employee Office"; room nav with icons; "Who's Here (n)" with avatar+name+status-dot+status-text; primary **Invite Agent** button pinned bottom.
- Top bar: CONNECT WEBCAM slot (v1: replaced by "Observe mode" toggle), clock, "● 52 online", people + settings icons.
- Center: Phaser canvas.
- Right rail (collapsible): **My Tasks** (icon, title, agent, ETA per row; + to add) and **Agent Status** (avatar, name, one-line current activity, status dot).
- Bottom: Atrium Bot toast (left), "Message Atrium…" input (center), quick actions: locate-me, people, screen, grid, fullscreen (right).

**Theme:** dark navy base (#0E1525-ish), panel #1A2233-ish, teal accent (buttons like CONNECT WEBCAM), status colors: green=online/working, orange=away/in-meeting, blue=in-focus. Pixel-art sprites; UI text is a clean sans (not pixel font). Exact tokens to be set in `web/src/theme.ts` — pick from the reference images at build time.

**Secondary surfaces (`reference-2-full-ui-composite.png`):** Agent Profile, My Workspace, Analytics, Chat, Focus Pod detail (DND timer + Exit Pod), Announcements, Task Flow — layouts per the image; build one component per milestone, not pixel-perfect clones but recognizably the same structure.

## 5. Explicitly out of scope for v1
- Webcam / screen sharing / whiteboard (SkyOffice features — stripped, may return later).
- Mobile browser support (SkyOffice limitation; dashboard should still be responsive).
- Autonomous legal/HR advice (Phase 4 introduces gated versions only).
- Agent-to-agent negotiation/marketplaces; fine-tuning custom models.

## 6. Naming note
"Atrium" is a working name; a trademark/domain check is a Phase 3 checklist item. All code should reference the product name from a single config constant so renaming is one change.
