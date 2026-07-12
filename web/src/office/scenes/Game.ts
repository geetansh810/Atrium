// Fork of upstream Game scene. Tile-map construction, Tiled object import,
// player movement and chair-sitting are upstream; the Colyseus network
// handlers were replaced by bridge-driven agent avatars (office = projection).
import Phaser from "phaser";

import { createCharacterAnims } from "../anims/CharacterAnims";

import Item from "../items/Item";
import Chair from "../items/Chair";
import Computer from "../items/Computer";
import Whiteboard from "../items/Whiteboard";
import VendingMachine from "../items/VendingMachine";
import "../characters/MyPlayer";
import MyPlayer from "../characters/MyPlayer";
import AgentAvatar from "../characters/AgentAvatar";
import PlayerSelector from "../characters/PlayerSelector";
import { PlayerBehavior } from "../types";
import type { NavKeys, Keyboard } from "../types";

import { Event, phaserEvents } from "../events";
import { officeBridge } from "../bridge";
import type { AgentPresence } from "../bridge";
import { PLAYER_SPAWN, ROOM_CAMERA } from "../officeLayout";

export default class Game extends Phaser.Scene {
  private cursors!: NavKeys;
  private keyE!: Phaser.Input.Keyboard.Key;
  private map!: Phaser.Tilemaps.Tilemap;
  myPlayer!: MyPlayer;
  private playerSelector!: PlayerSelector;
  private agentMap = new Map<string, AgentAvatar>();
  private cameraFollowing = true;

  constructor() {
    super("game");
  }

  registerKeys() {
    const keyboard = this.input.keyboard!;
    this.cursors = {
      ...keyboard.createCursorKeys(),
      ...(keyboard.addKeys("W,S,A,D") as Keyboard),
    };
    this.keyE = keyboard.addKey("E");
    keyboard.disableGlobalCapture();
  }

  create() {
    createCharacterAnims(this.anims);

    this.map = this.make.tilemap({ key: "tilemap" });
    const FloorAndGround = this.map.addTilesetImage("FloorAndGround", "tiles_wall")!;

    const groundLayer = this.map.createLayer("Ground", FloorAndGround)!;
    groundLayer.setCollisionByProperty({ collides: true });

    this.myPlayer = this.add.myPlayer(PLAYER_SPAWN.x, PLAYER_SPAWN.y, "adam", "me");
    this.myPlayer.setNameTag(officeBridge.playerName);
    this.playerSelector = new PlayerSelector(this, 0, 0, 16, 16);

    // import chair objects from Tiled map to Phaser
    const chairs = this.physics.add.staticGroup({ classType: Chair });
    const chairLayer = this.map.getObjectLayer("Chair")!;
    chairLayer.objects.forEach((chairObj) => {
      const item = this.addObjectFromTiled(chairs, chairObj, "chairs", "chair") as Chair;
      // custom properties[0] is the object direction specified in Tiled
      const props = chairObj.properties as { value: string }[];
      item.itemDirection = props[0].value;
    });

    // import computer / whiteboard / vending machine objects (decorative)
    const computers = this.physics.add.staticGroup({ classType: Computer });
    this.map.getObjectLayer("Computer")!.objects.forEach((obj) => {
      const item = this.addObjectFromTiled(computers, obj, "computers", "computer");
      item.setDepth(item.y + item.height * 0.27);
    });

    const whiteboards = this.physics.add.staticGroup({ classType: Whiteboard });
    this.map.getObjectLayer("Whiteboard")!.objects.forEach((obj) => {
      this.addObjectFromTiled(whiteboards, obj, "whiteboards", "whiteboard");
    });

    const vendingMachines = this.physics.add.staticGroup({ classType: VendingMachine });
    this.map.getObjectLayer("VendingMachine")!.objects.forEach((obj) => {
      this.addObjectFromTiled(vendingMachines, obj, "vendingmachines", "vendingmachine");
    });

    // import other objects from Tiled map to Phaser
    this.addGroupFromTiled("Wall", "tiles_wall", "FloorAndGround", false);
    this.addGroupFromTiled("Objects", "office", "Modern_Office_Black_Shadow", false);
    this.addGroupFromTiled("ObjectsOnCollide", "office", "Modern_Office_Black_Shadow", true);
    this.addGroupFromTiled("GenericObjects", "generic", "Generic", false);
    this.addGroupFromTiled("GenericObjectsOnCollide", "generic", "Generic", true);
    this.addGroupFromTiled("Basement", "basement", "Basement", true);

    this.cameras.main.zoom = 1.5;
    this.cameras.main.startFollow(this.myPlayer, true);

    this.physics.add.collider([this.myPlayer, this.myPlayer.playerContainer], groundLayer);
    this.physics.add.collider([this.myPlayer, this.myPlayer.playerContainer], vendingMachines);

    this.physics.add.overlap(
      this.playerSelector,
      [chairs, computers, whiteboards, vendingMachines],
      this.handleItemSelectorOverlap,
      undefined,
      this,
    );

    this.registerKeys();

    // bridge listeners (replace upstream network listeners)
    phaserEvents.on(Event.AGENTS_UPDATED, this.handleAgentsUpdated, this);
    phaserEvents.on(Event.FOCUS_ROOM, this.handleFocusRoom, this);
    phaserEvents.on(Event.KEYBOARD_ENABLED, this.handleKeyboardEnabled, this);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      phaserEvents.off(Event.AGENTS_UPDATED, this.handleAgentsUpdated, this);
      phaserEvents.off(Event.FOCUS_ROOM, this.handleFocusRoom, this);
      phaserEvents.off(Event.KEYBOARD_ENABLED, this.handleKeyboardEnabled, this);
    });

    // spawn the roster the store already knows about (bootstrap-from-state,
    // the mock analog of GET /office-state)
    this.syncAgents(officeBridge.agents, true);
    phaserEvents.emit(Event.GAME_READY);
  }

  private handleItemSelectorOverlap(
    playerSelector: Parameters<Phaser.Types.Physics.Arcade.ArcadePhysicsCallback>[0],
    selectionItem: Parameters<Phaser.Types.Physics.Arcade.ArcadePhysicsCallback>[1],
  ) {
    const selector = playerSelector as PlayerSelector;
    const item = selectionItem as Item;
    const currentItem = selector.selectedItem;
    // currentItem is undefined if nothing was previously selected
    if (currentItem) {
      // if the selection has not changed, do nothing
      if (currentItem === item || currentItem.depth >= item.depth) {
        return;
      }
      // if selection changes, clear previous dialog
      if (this.myPlayer.playerBehavior !== PlayerBehavior.SITTING) currentItem.clearDialogBox();
    }

    // set selected item and set up new dialog
    selector.selectedItem = item;
    item.onOverlapDialog();
  }

  private addObjectFromTiled(
    group: Phaser.Physics.Arcade.StaticGroup,
    object: Phaser.Types.Tilemaps.TiledObject,
    key: string,
    tilesetName: string,
  ) {
    const actualX = object.x! + object.width! * 0.5;
    const actualY = object.y! - object.height! * 0.5;
    const obj = group
      .get(actualX, actualY, key, object.gid! - this.map.getTileset(tilesetName)!.firstgid)
      .setDepth(actualY) as Item;
    return obj;
  }

  private addGroupFromTiled(
    objectLayerName: string,
    key: string,
    tilesetName: string,
    collidable: boolean,
  ) {
    const group = this.physics.add.staticGroup();
    const objectLayer = this.map.getObjectLayer(objectLayerName)!;
    objectLayer.objects.forEach((object) => {
      const actualX = object.x! + object.width! * 0.5;
      const actualY = object.y! - object.height! * 0.5;
      group
        .get(actualX, actualY, key, object.gid! - this.map.getTileset(tilesetName)!.firstgid)
        .setDepth(actualY);
    });
    if (this.myPlayer && collidable)
      this.physics.add.collider([this.myPlayer, this.myPlayer.playerContainer], group);
  }

  private handleAgentsUpdated(agents: AgentPresence[]) {
    this.syncAgents(agents, false);
  }

  // reconcile avatars against the roster: spawn, retarget, despawn
  private syncAgents(agents: AgentPresence[], snap: boolean) {
    const seen = new Set<string>();
    agents.forEach((presence) => {
      if (!presence.seat) return; // offline
      seen.add(presence.id);
      let avatar = this.agentMap.get(presence.id);
      if (!avatar) {
        avatar = new AgentAvatar(
          this,
          presence.seat.x,
          presence.seat.y,
          presence.texture,
          presence.id,
          presence.name,
        );
        this.add.existing(avatar);
        this.agentMap.set(presence.id, avatar);
        avatar.setSeat(presence.seat, true);
      } else {
        avatar.setSeat(presence.seat, snap);
      }
      avatar.setStatusColor(officeBridge.statusColors[presence.status]);
      if (!snap) avatar.setActivity(presence.activity);
    });

    this.agentMap.forEach((avatar, id) => {
      if (!seen.has(id)) {
        avatar.destroy();
        this.agentMap.delete(id);
      }
    });
  }

  // Sidebar room shortcut: pan the camera to the room; any movement key hands
  // the camera back to the player
  private handleFocusRoom(roomLabel: string) {
    const anchor = ROOM_CAMERA[roomLabel];
    if (!anchor) return;
    this.cameras.main.stopFollow();
    this.cameraFollowing = false;
    this.cameras.main.pan(anchor.x, anchor.y, 700, "Sine.easeInOut");
  }

  private handleKeyboardEnabled(enabled: boolean) {
    const keyboard = this.input.keyboard!;
    keyboard.enabled = enabled;
    if (!enabled) {
      keyboard.resetKeys();
      this.myPlayer.setVelocity(0, 0);
    }
  }

  update() {
    if (!this.myPlayer || !this.cursors) return;

    this.playerSelector.update(this.myPlayer, this.cursors);
    this.myPlayer.update(this.playerSelector, this.cursors, this.keyE);

    if (!this.cameraFollowing) {
      const anyKeyDown =
        this.cursors.left?.isDown ||
        this.cursors.right?.isDown ||
        this.cursors.up?.isDown ||
        this.cursors.down?.isDown ||
        this.cursors.W?.isDown ||
        this.cursors.A?.isDown ||
        this.cursors.S?.isDown ||
        this.cursors.D?.isDown;
      if (anyKeyDown) {
        this.cameraFollowing = true;
        this.cameras.main.pan(
          this.myPlayer.x,
          this.myPlayer.y,
          400,
          "Sine.easeInOut",
          false,
          (_camera, progress) => {
            if (progress === 1) this.cameras.main.startFollow(this.myPlayer, true);
          },
        );
      }
    }
  }
}
