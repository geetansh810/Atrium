// Upstream whiteboards opened a shared drawing canvas (stripped, M2.4b).
import { ItemType } from "../types";
import Item from "./Item";

export default class Whiteboard extends Item {
  constructor(scene: Phaser.Scene, x: number, y: number, texture: string, frame?: string | number) {
    super(scene, x, y, texture, frame);

    this.itemType = ItemType.WHITEBOARD;
  }
}
