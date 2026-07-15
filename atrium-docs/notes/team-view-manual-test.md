# Team view — 5-minute manual test (session 23)

The automated pass already verified: board layout in both modes, card → agent profile, task row → task drawer, `/office` → `/team` redirect, zero console errors, `make check` green. The one thing worth eyeballing yourself is the **live zone movement**, because interval polling only runs while the tab is focused (React Query default) — the automated browser tab reports itself hidden, so it couldn't watch the 3-second "Working now" flash.

## Mock mode (~1 min)
```bash
cd web && VITE_USE_MOCKS=1 npm run dev -- --port 5199
```
Open http://localhost:5199/team. Expect:
- **Working now (4):** ResearchAgent, DataAnalyst, EmailAgent, CoderAgent — each card shows its task, a progress bar, and "claimed N days ago".
- **Awaiting review (1):** HRAgent with "Refresh Onboarding Doc · completed N days ago".
- **Idle (1):** ReportAgent (no task row). No In focus / Paused / Offline sections (empty zones hide; the top two always show).
- Click a card body → that agent's profile. Click the inner task row → the task drawer opens instead (card click must NOT also fire).
- http://localhost:5199/office redirects to `/team`.

## API mode — the live test (~3 min)
Stack + dev server up (`docker compose up -d`, `cd web && npm run dev`), open http://localhost:5173/team **and keep the tab focused**. Current expected state (leftover real data): Priya + Rohan in **Awaiting review**, Rohan (v1) + Meera in **Offline**, "Nobody is working on a task right now" hint under Working now.

1. If agents were rebuilt/restarted recently, kick Priya's poll loop first: her profile → Settings → Pause, then Resume.
2. Header → New Task: any content brief, skill `content`. A longer brief (~200 words of output) widens the window.
3. Watch `/team` **without refreshing**:
   - within ~5s Priya's card appears under **Working now** ("claimed just now" + progress bar) — this is the 3-second flash the automated run kept missing;
   - a few seconds later she moves to **Awaiting review** ("completed just now");
   - approve the task in Review Inbox (`/tasks/review`) → on the next poll her card leaves Awaiting review (back to Idle once nothing else is pending).

Note: the tenant has ~7 real `pending_review` verification tasks left from this session's pass (Raksha Bandhan / Diwali copy, all genuinely Gemini-written) — harmless per the usual "leave it" precedent; approving them from Review Inbox is itself a fine way to watch the board move.

Known/accepted: zones derived from `agent.status` (In focus / Offline) only move on manual PATCHes — no `agent.status_changed` publisher exists yet. Polling pauses in unfocused tabs by design.
