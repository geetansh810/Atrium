// Fork of upstream OtherPlayer. Upstream lerped toward positions broadcast by
// Colyseus; AgentAvatar keeps that walk-to-target movement but the target is
// a seat resolved from agent status (office = projection of business state).
// WebRTC call logic was stripped. Straight-line walk v1 — pathfinding later.
import Phaser from "phaser";
import Player from "./Player";
import { Event, phaserEvents } from "../events";
import type { ResolvedSeat } from "../officeLayout";

const SPEED = 120; // slower than the human player — agents stroll

export default class AgentAvatar extends Player {
  private targetSeat: ResolvedSeat | null = null;
  private arrived = true;
  private lastActivity = "";

  constructor(
    scene: Phaser.Scene,
    x: number,
    y: number,
    texture: string,
    id: string,
    name: string,
    frame?: string | number,
  ) {
    super(scene, x, y, texture, id, frame);
    this.setNameTag(name);

    this.setInteractive({ useHandCursor: true });
    this.on("pointerup", () => {
      phaserEvents.emit(Event.AGENT_CLICKED, this.playerId);
    });
  }

  /** Walk to a seat (or snap instantly, used for the initial roster spawn). */
  setSeat(seat: ResolvedSeat, snap = false) {
    if (this.targetSeat?.seatId === seat.seatId) return;
    this.targetSeat = seat;
    this.arrived = false;
    if (snap) {
      this.x = seat.x;
      this.y = seat.y;
      this.settle();
    }
  }

  /** Show what the agent is doing as a chat-style bubble when it changes. */
  setActivity(activity: string) {
    if (!activity || activity === this.lastActivity) return;
    this.lastActivity = activity;
    this.updateDialogBubble(activity);
  }

  private settle() {
    if (!this.targetSeat) return;
    this.arrived = true;
    this.anims.play(`${this.playerTexture}_${this.targetSeat.anim}`, true);
    this.setDepth(this.targetSeat.depth ?? this.y);
  }

  destroy(fromScene?: boolean) {
    this.playerContainer.destroy();
    super.destroy(fromScene);
  }

  /** preUpdate is called every frame for every game object. */
  preUpdate(t: number, dt: number) {
    super.preUpdate(t, dt);

    if (!this.targetSeat || this.arrived) {
      this.playerContainer.setPosition(this.x, this.y - 30);
      return;
    }

    const delta = (SPEED / 1000) * dt; // minimum distance covered this frame
    let dx = this.targetSeat.x - this.x;
    let dy = this.targetSeat.y - this.y;

    // snap the axis once close enough, walk the remaining one
    if (Math.abs(dx) <= delta) {
      this.x = this.targetSeat.x;
      dx = 0;
    } else {
      this.x += Math.sign(dx) * delta;
    }
    if (dx === 0) {
      if (Math.abs(dy) <= delta) {
        this.y = this.targetSeat.y;
        dy = 0;
      } else {
        this.y += Math.sign(dy) * delta;
      }
    }

    this.setDepth(this.y);
    this.playerContainer.setPosition(this.x, this.y - 30);

    if (dx === 0 && dy === 0) {
      this.settle();
      return;
    }

    // face the dominant direction of travel
    if (dx !== 0) {
      this.anims.play(`${this.playerTexture}_run_${dx > 0 ? "right" : "left"}`, true);
    } else {
      this.anims.play(`${this.playerTexture}_run_${dy > 0 ? "down" : "up"}`, true);
    }
  }
}
