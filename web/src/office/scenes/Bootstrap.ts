// Fork of upstream Bootstrap: same asset preload, but with the lobby flow
// removed the game launches straight after preload (auto-join, M2.4b).
import Phaser from "phaser";

export default class Bootstrap extends Phaser.Scene {
  constructor() {
    super("bootstrap");
  }

  preload() {
    this.load.atlas(
      "cloud_day",
      "assets/background/cloud_day.png",
      "assets/background/cloud_day.json",
    );
    this.load.image("backdrop_day", "assets/background/backdrop_day.png");
    this.load.image("sun_moon", "assets/background/sun_moon.png");

    this.load.tilemapTiledJSON("tilemap", "assets/map/map.json");
    this.load.spritesheet("tiles_wall", "assets/map/FloorAndGround.png", {
      frameWidth: 32,
      frameHeight: 32,
    });
    this.load.spritesheet("chairs", "assets/items/chair.png", {
      frameWidth: 32,
      frameHeight: 64,
    });
    this.load.spritesheet("computers", "assets/items/computer.png", {
      frameWidth: 96,
      frameHeight: 64,
    });
    this.load.spritesheet("whiteboards", "assets/items/whiteboard.png", {
      frameWidth: 64,
      frameHeight: 64,
    });
    this.load.spritesheet("vendingmachines", "assets/items/vendingmachine.png", {
      frameWidth: 48,
      frameHeight: 72,
    });
    this.load.spritesheet("office", "assets/tileset/Modern_Office_Black_Shadow.png", {
      frameWidth: 32,
      frameHeight: 32,
    });
    this.load.spritesheet("basement", "assets/tileset/Basement.png", {
      frameWidth: 32,
      frameHeight: 32,
    });
    this.load.spritesheet("generic", "assets/tileset/Generic.png", {
      frameWidth: 32,
      frameHeight: 32,
    });
    this.load.spritesheet("adam", "assets/character/adam.png", {
      frameWidth: 32,
      frameHeight: 48,
    });
    this.load.spritesheet("ash", "assets/character/ash.png", {
      frameWidth: 32,
      frameHeight: 48,
    });
    this.load.spritesheet("lucy", "assets/character/lucy.png", {
      frameWidth: 32,
      frameHeight: 48,
    });
    this.load.spritesheet("nancy", "assets/character/nancy.png", {
      frameWidth: 32,
      frameHeight: 48,
    });
  }

  create() {
    this.scene.launch("background");
    this.scene.launch("game");
  }
}
