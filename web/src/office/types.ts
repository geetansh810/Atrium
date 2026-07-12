// Merged from upstream SkyOffice `types/` package (PlayerBehavior, Items,
// KeyboardState) — the Colyseus schema types stayed behind with the server.
// Upstream enums became const objects (tsconfig has erasableSyntaxOnly).
import Phaser from "phaser";

export const PlayerBehavior = {
  IDLE: 0,
  SITTING: 1,
} as const;
export type PlayerBehavior = (typeof PlayerBehavior)[keyof typeof PlayerBehavior];

export const ItemType = {
  CHAIR: 0,
  COMPUTER: 1,
  WHITEBOARD: 2,
  VENDINGMACHINE: 3,
} as const;
export type ItemType = (typeof ItemType)[keyof typeof ItemType];

export type Keyboard = {
  W: Phaser.Input.Keyboard.Key;
  S: Phaser.Input.Keyboard.Key;
  A: Phaser.Input.Keyboard.Key;
  D: Phaser.Input.Keyboard.Key;
};

export type NavKeys = Keyboard & Phaser.Types.Input.Keyboard.CursorKeys;
