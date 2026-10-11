# 3 · UI tour

The web app is a React 19 + Vite single-page app in [`web/`](../../web/). Signed-out visitors see a landing page and the signup/login screen; signed-in users get the shell below. Routes are declared in one file: [`web/src/shell/routes.tsx`](../../web/src/shell/routes.tsx).

## First run

1. **Signup** ([`pages/auth/AuthPage.tsx`](../../web/src/pages/auth/AuthPage.tsx)) creates a company and its first admin and signs you in. Log-in is the same page in a different mode.
2. A **fresh signup** (never a login) opens the **onboarding wizard** ([`pages/onboarding/OnboardingWizard.tsx`](../../web/src/pages/onboarding/OnboardingWizard.tsx)): pick a starter pack (Engineering pod or Content team, defined in [`shared/starterPacks.ts`](../../web/src/shared/starterPacks.ts)) → agents are hired in sequence with the lead as manager → assign a first task → success screen. "Skip for now" lands on an empty dashboard.

## The shell

[`shell/NavSidebar.tsx`](../../web/src/shell/NavSidebar.tsx) is the primary navigation; [`shell/GlobalHeader.tsx`](../../web/src/shell/GlobalHeader.tsx) carries the page title, **Cmd/Ctrl+K** command palette ([`ui/CommandPalette.tsx`](../../web/src/ui/CommandPalette.tsx)), Chat, Notifications, Settings, and the New Task / Hire Agent buttons.

## Screens

| Nav item | Route | What it is for | Data source (API mode) |
|---|---|---|---|
| **Mission Control** | `/` | Company pulse, KPI tiles, the live task, workflow preview, agent status grid, company budget burn, activity feed. A banner appears when queued tasks sit unclaimed for 2+ minutes. | Roster + tasks + events + budget, polled every ~4s |
| **Tasks** | `/tasks`, `/tasks/:id` | Kanban board (Queued / In Progress / Review / Done). Click a card for the drawer: Overview, Subtasks, Output, Timeline (the audit trail), Conversation. Approve and reject live here. Dragging is limited to Review→Done; agents drive every other status. | `/companies/{id}/tasks`, `/tasks/{id}`, `/tasks/{id}/events` |
| **Review inbox** | `/tasks/review` | Everything needing a human: `pending_review` and `flagged` tasks, plus a **Memories** tab for lessons/facts awaiting approval. | `/escalations`, `/memories/review-queue` |
| **Workflow** | `/workflow` | The parent→child task graph for a decomposed request, with live node status. The "Workflow Library" card is sample data. | `/tasks/{id}/flow` |
| **Employees** | `/employees`, `/employees/:id` | Roster cards; the profile has Overview, Tasks, Activity, Conversation, Memory, Performance, Settings (pause/resume, exit focus pod). | roster, `/analytics/agent-performance`, `/memories?agentId=` |
| **Organization** | `/organization` | Org chart (manager tree, collapsible) and the **Payroll** tab: per-agent and company token caps (editable) and cost per task for a chosen month. | `/budget`, `/analytics/cost-per-task` |
| **Projects** | `/projects`, `/projects/:id` | Groups real tasks under a project with a budget cap. **Mock-only** — there is no projects backend; API mode shows a "Sample data" pill. | local fixtures |
| **Knowledge** | `/knowledge` | Search the company's reference documents; in API mode backed by the real ingest/list/archive endpoints. | `/companies/{id}/knowledge` |
| **Reports** | `/reports` | KPIs, 7-day task trend, top skills, agent leaderboard, budget burn. Focus Time is not tracked (no presence source), and says so. | `/analytics/*` |
| **LLM Logs** | `/logs` | Every LLM call with its full prompt, messages, tools, response, tokens and latency; filter OK/Errors. Live-API only. | `/companies/{id}/llm-logs` |
| **Team** | `/team` | Live board of who is Working / Awaiting review / In focus / Idle / Paused / Offline, **derived** from task state, never stored. | derived in [`shared/selectors.ts`](../../web/src/shared/selectors.ts) (`liveTeamZones`) |
| — | `/notifications` | Task activity merged with announcements; post an announcement. | tasks + `/announcements` |
| — | `/settings` | Company info, model catalog, credits, log out. | `/companies/{id}`, `/model-catalog` |
| Chat drawer | (header icon) | Channels and DMs; the bot posts "🤖 {agent} finished '{title}'" to `#general`. | `/channels`, `/channels/{id}/messages` |

## Two modes, one contract

The app has two interchangeable state providers behind the same `AppState`/`Action` contract ([`shared/storeTypes.ts`](../../web/src/shared/storeTypes.ts)): [`mockStore.tsx`](../../web/src/shared/mockStore.tsx) (fixtures, `VITE_USE_MOCKS=1`, no login) and [`apiStore.tsx`](../../web/src/shared/apiStore.tsx) (React Query over [`shared/api.ts`](../../web/src/shared/api.ts), the default). Anything with no backend yet (Projects, workflow templates) is visibly tagged **Sample data** rather than faked. Design and motion rules: [`web/src/index.css`](../../web/src/index.css) and [`shared/theme.ts`](../../web/src/shared/theme.ts) — all colors come from CSS variables.

## Walkthrough: the five-minute demo

1. Sign up → pick **Engineering pod**.
2. Assign the pre-filled task. Open **Tasks** — it sits in *Queued* until an agent's next poll (~15s).
3. Watch **Mission Control** or **Team**: the agent moves to *Working now*, then the task to *Review*.
4. Open the task: **Output** shows the artifact, **Timeline** shows `created → claimed → progress → completed`.
5. **Approve** (or **Reject** with feedback and watch it requeue and return with the feedback in its prompt).
6. **LLM Logs** shows the exact prompt that was sent; **Organization → Payroll** shows what it cost.

If step 2 never moves, the agent's provider has no API key — see [FAQ](10-faq.md#why-is-my-task-stuck-in-queued).

*Verified against:* [`web/src/shell/`](../../web/src/shell/), [`web/src/pages/`](../../web/src/pages/), [`web/src/shared/apiStore.tsx`](../../web/src/shared/apiStore.tsx).
