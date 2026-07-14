# frontend-shared

**What:** `web/src/shared/` — the single source for tokens, types, mock data, and app state.

**State (2026-07-14, MF-4): contract now carries `Agent.paused`; nav.ts is the routing indirection layer.**
- `theme.ts` — design tokens (navy base, panel, teal accent, status colors, avatar palette, plus MF-2's `--node-*`/`--glow-running`/`--surface-{1,2,3}`), mirrored as CSS vars in `index.css`. **No hardcoded hex in components** (grep-verified every MF session).
- `types.ts` — TS types mirroring [[data-model]] columns (Agent incl. `paused` since MF-4, Task+Subtask+`TaskEvent`/`events[]` since MF-2, ActivityEvent, Channel/ChatMessage, Announcement, Budget, RoleTemplate, analytics shapes…). Never redeclare shapes ad hoc.
- `mocks/*.json` — the mock "database": agents+stats (every agent now has `paused: false`), tasks (all statuses incl. flagged/pending_review + a parent→children chain, each with a synthetic `events` chain since MF-2), activity, chat, announcements, analytics, budgets, roleTemplates, company.
- `mockData.ts` — typed loader over the JSON; each export maps 1:1 to a `04-api-contract` endpoint. Used by `mockStore.tsx` (`VITE_USE_MOCKS=1`) and, for chat/announcements/analytics only, by `apiStore.tsx` too (no backend endpoint yet).
- `api.ts`/`adapters.ts`/`queries.ts` (M0.8, extended every MF session since) — the real-API equivalent: typed fetch client, DTO adapters, React Query hooks. Consumed only by `apiStore.tsx`. `adaptAgent` now carries `paused` through (MF-4 fix — the DTO always had it, the adapter silently dropped it); `usePatchAgentMutation` takes `{agentId, status?, paused?}`.
- `store.tsx` — provider swap point (M0.8): re-exports `mockStore.tsx`'s `useReducer` provider or `apiStore.tsx`'s React Query provider, picked once by `config.USE_MOCKS`. Both implement the same `AppState`/`Action` contract (`storeTypes.ts`) — actions (createTask, approve/reject, inviteAgent, sendMessage, setBudgetCap, exitFocusPod, `setAgentPaused` since MF-4…) plus UI state (`activePanel: PanelKey | null` — shrunk to 4 values by MF-4: `analytics`/`chat`/`announcements`/`flow`, room, channel) are identical either way.
- `nav.ts` (MF-1, since generalized each MF session) — `useAppNav()`'s `openTask`/`openAgent`/`openChat` are the one indirection between components and navigation; `openTask` (MF-3) and `openAgent` (MF-4) now do real `useNavigate()` routing, only `openChat` still dispatches a legacy `openPanel` (pending MF-6).
- `selectors.ts` (MF-2) — pure derivations with no `storeTypes.ts` dependency, safe from any page: `computeKpis`, `companyBudgetBurn`, `companyPulse`, `liveMissionTask`, `synthesizeFeed`, `kanbanColumns` (consumed by MF-3's Tasks board), `orgTree` (consumed by MF-4's Organization page).
- `ConversationThread` and `Kanban` don't live here (they're `ui/` primitives, not shared state) — see [[web-dashboard]].
- Atoms: `icons.tsx` (~30 line icons), `StatusDot.tsx`, `Avatar.tsx` (deterministic tint), `ProgressBar.tsx`, `skillColor.ts`, `format.ts` (timeAgo, tokens, durations).

**Status palette:** green=online/working · orange=away/in_meeting · blue=in_focus · red-ish=flagged · grey=offline.

Links: [[_Atrium]] · [[web-dashboard]] · [[data-model]]
