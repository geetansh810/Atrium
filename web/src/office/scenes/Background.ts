// Fork of upstream Background, day mode only (night assets left upstream).
import Phaser from "phaser";

export default class Background extends Phaser.Scene {
  private cloud!: Phaser.Physics.Arcade.Group;

  constructor() {
    super("background");
  }

  create() {
    const sceneHeight = this.cameras.main.height;
    const sceneWidth = this.cameras.main.width;

    this.cameras.main.setBackgroundColor("#c6eefc");

    // Add backdrop image
    const backdropImage = this.add.image(sceneWidth / 2, sceneHeight / 2, "backdrop_day");
    const scale = Math.max(sceneWidth / backdropImage.width, sceneHeight / backdropImage.height);
    backdropImage.setScale(scale).setScrollFactor(0);

    // Add sun image
    const sunMoonImage = this.add.image(sceneWidth / 2, sceneHeight / 2, "sun_moon");
    const scale2 = Math.max(sceneWidth / sunMoonImage.width, sceneHeight / sunMoonImage.height);
    sunMoonImage.setScale(scale2).setScrollFactor(0);

    // Add 24 clouds at random positions and with random speeds
    const frames = this.textures.get("cloud_day").getFrameNames();
    this.cloud = this.physics.add.group();
    for (let i = 0; i < 24; i++) {
      const x = Phaser.Math.RND.between(-sceneWidth * 0.5, sceneWidth * 1.5);
      const y = Phaser.Math.RND.between(sceneHeight * 0.2, sceneHeight * 0.8);
      const velocity = Phaser.Math.RND.between(15, 30);

      this.cloud
        .get(x, y, "cloud_day", frames[i % 6])
        .setScale(3)
        .setVelocity(velocity, 0);
    }
  }

  update() {
    this.physics.world.wrap(this.cloud, 500);
  }
}
