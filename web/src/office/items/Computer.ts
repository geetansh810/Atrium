// Upstream computers opened a screen-share dialog; here they are the agents'
// workstations — decorative until the office is wired to real task detail.
import { ItemType } from "../types";
import Item from "./Item";

export default class Computer extends Item {
  constructor(scene: Phaser.Scene, x: number, y: number, texture: string, frame?: string | number) {
    super(scene, x, y, texture, frame);

    this.itemType = ItemType.COMPUTER;
  }
}
