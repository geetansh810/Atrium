// Fork of upstream `events/EventCenter.ts`. Upstream used this bus for
// Phaser ↔ Redux traffic; here it carries Atrium's React ↔ Phaser traffic.
// Upstream enum became a const object (tsconfig has erasableSyntaxOnly).
import Phaser from "phaser";

export const phaserEvents = new Phaser.Events.EventEmitter();

export const Event = {
  AGENTS_UPDATED: "agents-updated",
  AGENT_CLICKED: "agent-clicked",
  FOCUS_ROOM: "focus-room",
  KEYBOARD_ENABLED: "keyboard-enabled",
  GAME_READY: "game-ready",
} as const;
export type Event = (typeof Event)[keyof typeof Event];
