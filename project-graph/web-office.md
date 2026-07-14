# web-office

**What:** `web/src/office/` — the pixel-art office view. A **FORK of SkyOffice's client** (Phaser 3), never rebuilt by hand. See `web/src/office/README.md` for what was kept/stripped/added.

**State (2026-07-14, updated for MF-4): BUILT, mock-driven, one optional route since MF-1.** Vendored from SkyOffice commit `3f66b8b` (client only; their `server/` will be vendored when [[office-realtime]] starts). Since MF-1 it mounts via `pages/office/OfficePage.tsx` (not directly into the old `DashboardLayout`) — one route among many in the redesigned [[web-dashboard]] shell, never the primary interface. This did the client half of M2.4a (vendor & boot) + M2.4b (strip webcam/PeerJS/lobby) early, and a mock-driven preview of M2.4c: `AgentAvatar`s (fork of upstream OtherPlayer) walk to seats resolved from store state — Exit Pod (now in `EmployeeProfile`'s Settings tab, MF-4) makes the agent walk pod → desk, verified in browser.

**Key pieces:**
- `bridge.ts` — React ↔ Phaser doorway (replaced upstream `Network.ts`); the Colyseus client plugs in here at M2.4c, scenes unchanged.
- `officeLayout.ts` — locationKey/status → seat coords (position IS status); seed data for the future `office_layout` table. Camera anchors keyed by `OfficePage.tsx`'s room-nav chips (page-local since MF-1, previously Sidebar-owned).
- `OfficeCanvas.tsx` — defers game creation until the container has size (StrictMode/0×0-renderer guard); syncs store→bridge; disables Phaser keys while typing in dashboard inputs. **Presentation-only since MF-1** — the `onAgentClicked` handler lives in `OfficePage.tsx`, not here, and calls `nav.openAgent(agentId)`, which since MF-4 does a real `navigate('/employees/${id}')` (was a legacy `openPanel profile` dispatch through MF-1–3).
- `createGame.ts` has a boot watchdog for embedded-Chromium texture-READY loss (no-op in normal browsers). Note: in a hidden tab Phaser's RAF loop freezes (normal; self-recovers on focus).

**Assets:** `web/public/assets/` (1.7MB, LimeZu). ⚠️ Free tier = non-commercial; **paid packs must be bought before launch** — logged in 06 §D, gate at M3.5.

**Still pending (real M2.4a–c):** vendor SkyOffice `server/` as office-realtime · company-scoped room join with core-api token · Redis-driven presence replacing mock store · pathfinding (walk is straight-line v1).

**Contracts:** `atrium-docs/06-open-source-reuse.md §A` · `02-architecture.md §4`.

Links: [[_Atrium]] · [[web-dashboard]] · [[office-realtime]] · [[realtime-events]] · [[milestones]] · [[frontend-shared]]
