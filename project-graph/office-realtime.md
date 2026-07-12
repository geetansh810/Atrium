# office-realtime

**What:** TypeScript / Colyseus service — **fork of SkyOffice's `server/`**. Presence, movement, chat rooms, and the agent-avatar driver. **Stateless: never writes business state**; kill it and it rebuilds from `GET /companies/{id}/office-state`.

**State: NOT STARTED.** Milestones M2.4a (vendor), M2.4b (strip + identity), M2.4c (agent avatars).

**Key components:** `OfficeRoom.onCreate` → fetch office-state, spawn avatars · `EventSubscriber` (Redis, [[realtime-events]]) → `AgentAvatarDriver.apply(event)` → target tile from `office_layout` table, straight-line tween, status dot + activity bubble.

**Key rules:** one room per company `office:{companyId}`; join requires core-api-issued HMAC room token (`OFFICE_ROOM_TOKEN_SECRET`), company mismatch → reject. Status→location map is DATA (`office_layout` table): working→desk · in_meeting→meeting_room_alpha · in_focus→focus_pod_n · away→cafe · flagged→help_desk · offline→despawn.

**Contracts:** `atrium-docs/02-architecture.md §4` · `05-module-specs.md §office-realtime` · `06-open-source-reuse.md §A`.

Links: [[_Atrium]] · [[web-office]] · [[realtime-events]] · [[core-api]] · [[milestones]]
