// Fork of upstream PhaserGame.ts, turned into a factory so React owns the
// game's lifecycle (mount/unmount) instead of a module-level singleton.
import Phaser from "phaser";
import Background from "./scenes/Background";
import Bootstrap from "./scenes/Bootstrap";
import Game from "./scenes/Game";

export function createOfficeGame(parent: HTMLElement): Phaser.Game {
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    backgroundColor: "#93cbee",
    pixelArt: true, // Prevent pixel art from becoming blurred when scaled.
    scale: {
      mode: Phaser.Scale.ScaleModes.RESIZE,
      width: parent.clientWidth || 800,
      height: parent.clientHeight || 600,
    },
    physics: {
      default: "arcade",
      arcade: {
        gravity: { x: 0, y: 0 },
        debug: false,
      },
    },
    autoFocus: true,
    scene: [Bootstrap, Background, Game],
  });
  if (import.meta.env.DEV) {
    // debug handle, same spirit as upstream's `window.game`
    (window as unknown as { officeGame?: Phaser.Game }).officeGame = game;
  }

  // Some embedded Chromium environments drop one of the three base64
  // default-texture loads (or the READY they gate) that Phaser fires at boot,
  // so Game.start() is never reached and the canvas stays blank. Re-add any
  // texture that never arrived, and re-emit the texture READY event once all
  // three exist. No-op in healthy browsers: the game is running by the first
  // tick and the watchdog unhooks itself.
  const healBoot = () => {
    if (game.isRunning || !game.isBooted) return;
    const { defaultImage, missingImage, whiteImage } = game.config;
    const defaults = [
      ["__DEFAULT", defaultImage],
      ["__MISSING", missingImage],
      ["__WHITE", whiteImage],
    ] as const;
    const missing = defaults.filter(([key]) => !game.textures.exists(key));
    if (missing.length === 0) {
      game.textures.emit(Phaser.Textures.Events.READY);
    } else {
      missing.forEach(([key, uri]) => {
        if (typeof uri === "string") game.textures.addBase64(key, uri);
      });
    }
  };
  const healTimer = window.setInterval(healBoot, 1000);
  const stopHealing = () => window.clearInterval(healTimer);
  game.events.once(Phaser.Core.Events.READY, stopHealing);
  game.events.once(Phaser.Core.Events.DESTROY, stopHealing);

  return game;
}
