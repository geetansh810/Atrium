// Fork of upstream MyPlayer: keyboard movement + `E` to sit are kept;
// Colyseus position broadcasting, PeerJS wiring, mobile joystick, and the
// computer/whiteboard `R` interactions were stripped (M2.4b scope).
import Phaser from "phaser";
import PlayerSelector from "./PlayerSelector";
import { PlayerBehavior, ItemType } from "../types";
import Player, { sittingShiftData } from "./Player";
import Chair from "../items/Chair";
import type { NavKeys } from "../types";

export default class MyPlayer extends Player {
  private playContainerBody: Phaser.Physics.Arcade.Body;
  private chairOnSit?: Chair;

  constructor(
    scene: Phaser.Scene,
    x: number,
    y: number,
    texture: string,
    id: string,
    frame?: string | number,
  ) {
    super(scene, x, y, texture, id, frame);
    this.playContainerBody = this.playerContainer.body as Phaser.Physics.Arcade.Body;
  }

  update(playerSelector: PlayerSelector, cursors: NavKeys, keyE: Phaser.Input.Keyboard.Key) {
    if (!cursors) return;

    const item = playerSelector.selectedItem;
    const body = this.body as Phaser.Physics.Arcade.Body;

    switch (this.playerBehavior) {
      case PlayerBehavior.IDLE: {
        // if press E in front of selected chair
        if (Phaser.Input.Keyboard.JustDown(keyE) && item?.itemType === ItemType.CHAIR) {
          const chairItem = item as Chair;
          /**
           * move player to the chair and play sit animation
           * a delay is called to wait for player movement (from previous velocity) to end
           * as the player tends to move one more frame before sitting down causing player
           * not sitting at the center of the chair
           */
          this.scene.time.addEvent({
            delay: 10,
            callback: () => {
              // update character velocity and position
              this.setVelocity(0, 0);
              if (chairItem.itemDirection) {
                this.setPosition(
                  chairItem.x + sittingShiftData[chairItem.itemDirection][0],
                  chairItem.y + sittingShiftData[chairItem.itemDirection][1],
                ).setDepth(chairItem.depth + sittingShiftData[chairItem.itemDirection][2]);
                // also update playerNameContainer velocity and position
                this.playContainerBody.setVelocity(0, 0);
                this.playerContainer.setPosition(
                  chairItem.x + sittingShiftData[chairItem.itemDirection][0],
                  chairItem.y + sittingShiftData[chairItem.itemDirection][1] - 30,
                );
              }

              this.play(`${this.playerTexture}_sit_${chairItem.itemDirection}`, true);
              playerSelector.selectedItem = undefined;
              if (chairItem.itemDirection === "up") {
                playerSelector.setPosition(this.x, this.y - this.height);
              } else {
                playerSelector.setPosition(0, 0);
              }
            },
            loop: false,
          });
          // set up new dialog as player sits down
          chairItem.clearDialogBox();
          chairItem.setDialogBox("Press E to leave");
          this.chairOnSit = chairItem;
          this.playerBehavior = PlayerBehavior.SITTING;
          return;
        }

        const speed = 200;
        let vx = 0;
        let vy = 0;

        if (cursors.left?.isDown || cursors.A?.isDown) vx -= speed;
        if (cursors.right?.isDown || cursors.D?.isDown) vx += speed;
        if (cursors.up?.isDown || cursors.W?.isDown) {
          vy -= speed;
          this.setDepth(this.y); //change player.depth if player.y changes
        }
        if (cursors.down?.isDown || cursors.S?.isDown) {
          vy += speed;
          this.setDepth(this.y); //change player.depth if player.y changes
        }
        // update character velocity
        this.setVelocity(vx, vy);
        body.velocity.setLength(speed);
        // also update playerNameContainer velocity
        this.playContainerBody.setVelocity(vx, vy);
        this.playContainerBody.velocity.setLength(speed);

        // update animation according to velocity
        if (vx > 0) {
          this.play(`${this.playerTexture}_run_right`, true);
        } else if (vx < 0) {
          this.play(`${this.playerTexture}_run_left`, true);
        } else if (vy > 0) {
          this.play(`${this.playerTexture}_run_down`, true);
        } else if (vy < 0) {
          this.play(`${this.playerTexture}_run_up`, true);
        } else {
          const currentKey = this.anims.currentAnim?.key;
          if (currentKey) {
            const parts = currentKey.split("_");
            parts[1] = "idle";
            const newAnim = parts.join("_");
            // this prevents idle animation keeps getting called
            if (currentKey !== newAnim) {
              this.play(newAnim, true);
            }
          }
        }
        break;
      }

      case PlayerBehavior.SITTING: {
        // back to idle if player press E while sitting
        if (Phaser.Input.Keyboard.JustDown(keyE)) {
          const currentKey = this.anims.currentAnim?.key;
          if (currentKey) {
            const parts = currentKey.split("_");
            parts[1] = "idle";
            this.play(parts.join("_"), true);
          }
          this.playerBehavior = PlayerBehavior.IDLE;
          this.chairOnSit?.clearDialogBox();
          playerSelector.setPosition(this.x, this.y);
          playerSelector.update(this, cursors);
        }
        break;
      }
    }
  }
}

declare global {
  namespace Phaser.GameObjects {
    interface GameObjectFactory {
      myPlayer(x: number, y: number, texture: string, id: string, frame?: string | number): MyPlayer;
    }
  }
}

Phaser.GameObjects.GameObjectFactory.register(
  "myPlayer",
  function (
    this: Phaser.GameObjects.GameObjectFactory,
    x: number,
    y: number,
    texture: string,
    id: string,
    frame?: string | number,
  ) {
    const sprite = new MyPlayer(this.scene, x, y, texture, id, frame);

    this.displayList.add(sprite);
    this.updateList.add(sprite);

    this.scene.physics.world.enableBody(sprite, Phaser.Physics.Arcade.DYNAMIC_BODY);

    const collisionScale = [0.5, 0.2];
    const body = sprite.body as Phaser.Physics.Arcade.Body;
    body
      .setSize(sprite.width * collisionScale[0], sprite.height * collisionScale[1])
      .setOffset(
        sprite.width * (1 - collisionScale[0]) * 0.5,
        sprite.height * (1 - collisionScale[1]),
      );

    return sprite;
  },
);
