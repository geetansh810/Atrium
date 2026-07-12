# web/src/office — forked from SkyOffice

Vendored fork of the SkyOffice client (MIT), per atrium-docs/06-open-source-reuse.md §A.

- **Upstream:** github.com/kevinshen56714/SkyOffice · commit `3f66b8bffad889ee9fc2340f9bcad28146299f47` (fetched 2026-07-12)
- **License:** MIT — keep upstream copyright; courtesy credit to SkyOffice in-app (pending credits screen, M3.5)
- **Assets:** `web/public/assets/` (map, tilesets, characters, items, background) copied from upstream `client/public/assets/` minus `archive/` and `character/single/` (lobby-only art). LimeZu pixel art — license status tracked in 06 §D.

## What was kept / stripped / added

**Kept (adapted to strict TS + Phaser 3.90):** Bootstrap/Background/Game scenes, tile-map + Tiled object import, MyPlayer WASD/arrow movement + `E` to sit, PlayerSelector, character animations, name tags, dialog bubbles, item classes.

**Stripped (M2.4b scope, done at vendor time since there is no server yet):** Colyseus `Network`, PeerJS/WebRTC/webcam/screen-share, whiteboard/computer dialogs, lobby/room-selection UI, MUI components, Redux stores, mobile joystick.

**Added (Atrium):** `bridge.ts` (React ↔ Phaser, replaces Network — the swap point for the future Colyseus client), `officeLayout.ts` (locationKey/status → seat coordinates; seed data for the future `office_layout` table), `AgentAvatar` (fork of upstream `OtherPlayer`: walks to server-driven targets, status dot, activity bubble, click → profile panel), `OfficeCanvas.tsx` (React mount).

**Office = projection:** nothing in here writes business state; it only renders what the store says. When office-realtime exists (M2.4c), `bridge.ts` is where its Colyseus client plugs in.
