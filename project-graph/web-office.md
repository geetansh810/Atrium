# web-office

**What:** `web/src/office/` — the pixel-art office view. A **FORK of SkyOffice's client** (Phaser 3), never rebuilt by hand. See `web/src/office/README.md` for what was kept/stripped/added.

**State (2026-07-12): BUILT, mock-driven.** Vendored from SkyOffice commit `3f66b8b` (client only; their `server/` will be vendored when [[office-realtime]] starts). Mounts into [[web-dashboard]] via `OfficeCanvas.tsx`. This did the client half of M2.4a (vendor & boot) + M2.4b (strip webcam/PeerJS/lobby) early, and a mock-driven preview of M2.4c: `AgentAvatar`s (fork of upstream OtherPlayer) walk to seats resolved from store state — Exit Pod in the dashboard makes the agent walk pod → desk, verified in browser.

**Key pieces:**
- `bridge.ts` — React ↔ Phaser doorway (replaced upstream `Network.ts`); the Colyseus client plugs in here at M2.4c, scenes unchanged.
- `officeLayout.ts` — locationKey/status → seat coords (position IS status); seed data for the future `office_layout` table. Camera anchors keyed by Sidebar room labels.
- `OfficeCanvas.tsx` — defers game creation until the container has size (StrictMode/0×0-renderer guard); syncs store→bridge; avatar click → profile panel; disables Phaser keys while typing in dashboard inputs.
- `createGame.ts` has a boot watchdog for embedded-Chromium texture-READY loss (no-op in normal browsers). Note: in a hidden tab Phaser's RAF loop freezes (normal; self-recovers on focus).

**Assets:** `web/public/assets/` (1.7MB, LimeZu). ⚠️ Free tier = non-commercial; **paid packs must be bought before launch** — logged in 06 §D, gate at M3.5.

**Still pending (real M2.4a–c):** vendor SkyOffice `server/` as office-realtime · company-scoped room join with core-api token · Redis-driven presence replacing mock store · pathfinding (walk is straight-line v1).

**Contracts:** `atrium-docs/06-open-source-reuse.md §A` · `02-architecture.md §4`.

Links: [[_Atrium]] · [[web-dashboard]] · [[office-realtime]] · [[realtime-events]] · [[milestones]] · [[frontend-shared]]
