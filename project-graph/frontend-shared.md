# frontend-shared

**What:** `web/src/shared/` — the single source for tokens, types, mock data, and app state.

**State (2026-07-12): BUILT (v2 — full mock backend).**
- `theme.ts` — design tokens (navy base, panel, teal accent, status colors, avatar palette), mirrored as CSS vars in `index.css`. **No hardcoded hex in components.**
- `types.ts` — TS types mirroring [[data-model]] columns (Agent, Task+Subtask, ActivityEvent, Channel/ChatMessage, Announcement, Budget, RoleTemplate, analytics shapes…). Never redeclare shapes ad hoc.
- `mocks/*.json` — the mock "database": agents+stats, tasks (all statuses incl. flagged/pending_review + a parent→children chain), activity, chat, announcements, analytics, budgets, roleTemplates, company.
- `mockData.ts` — typed loader over the JSON; each export maps 1:1 to a `04-api-contract` endpoint. Used by `mockStore.tsx` (`VITE_USE_MOCKS=1`) and, for chat/announcements/analytics only, by `apiStore.tsx` too (no backend endpoint yet).
- `api.ts`/`adapters.ts`/`queries.ts` (M0.8) — the real-API equivalent: typed fetch client, DTO adapters, React Query hooks. Consumed only by `apiStore.tsx`.
- `store.tsx` — provider swap point (M0.8): re-exports `mockStore.tsx`'s `useReducer` provider or `apiStore.tsx`'s React Query provider, picked once by `config.USE_MOCKS`. Both implement the same `AppState`/`Action` contract (`storeTypes.ts`) — actions (createTask, approve/reject, inviteAgent, sendMessage, setBudgetCap, exitFocusPod…) plus UI state (activePanel, room, channel) are identical either way.
- Atoms: `icons.tsx` (~30 line icons), `StatusDot.tsx`, `Avatar.tsx` (deterministic tint), `ProgressBar.tsx`, `skillColor.ts`, `format.ts` (timeAgo, tokens, durations).

**Status palette:** green=online/working · orange=away/in_meeting · blue=in_focus · red-ish=flagged · grey=offline.

Links: [[_Atrium]] · [[web-dashboard]] · [[data-model]]
