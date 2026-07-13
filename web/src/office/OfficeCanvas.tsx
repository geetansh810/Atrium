// React mount for the forked SkyOffice canvas. Everything flows one way:
// store state -> bridge -> Phaser (office = projection). Clicking an avatar
// is the only signal coming back, and it's the caller's job to handle it
// (OfficePage wires it to navigation) — this component is presentation-only.
import { useEffect, useRef } from "react";
import type Phaser from "phaser";
import { createOfficeGame } from "./createGame";
import { Event, phaserEvents } from "./events";
import { officeBridge } from "./bridge";
import type { AgentPresence } from "./bridge";
import { resolveSeats } from "./officeLayout";
import { currentUser } from "../shared/mockData";
import { useApp } from "../shared/store";
import type { Agent, AgentStatus } from "../shared/types";
import "./OfficeCanvas.css";

const STATUS_KEYS: AgentStatus[] = ["online", "working", "in_meeting", "in_focus", "away", "offline"];

function readStatusColors(): Partial<Record<AgentStatus, number>> {
  const rootStyle = getComputedStyle(document.documentElement);
  const colors: Partial<Record<AgentStatus, number>> = {};
  STATUS_KEYS.forEach((status) => {
    const raw = rootStyle.getPropertyValue(`--status-${status.replace("_", "-")}`).trim();
    if (/^#[0-9a-fA-F]{6}$/.test(raw)) colors[status] = parseInt(raw.slice(1), 16);
  });
  return colors;
}

function buildPresence(agents: Agent[]): AgentPresence[] {
  const seats = resolveSeats(agents);
  return agents.map((agent) => ({
    id: agent.id,
    name: agent.name,
    texture: agent.spriteKey,
    status: agent.status,
    activity: agent.currentActivity,
    seat: seats.get(agent.id) ?? null,
  }));
}

function isTypingTarget(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target instanceof HTMLInputElement ||
      target instanceof HTMLTextAreaElement ||
      target instanceof HTMLSelectElement ||
      target.isContentEditable)
  );
}

export function OfficeCanvas() {
  const { state } = useApp();
  const containerRef = useRef<HTMLDivElement>(null);
  // Read at GAME_READY time, not at the effect's mount-time closure, so a
  // remount on a non-default room (e.g. leaving /office and coming back)
  // still pans the fresh scene to the room the user was actually in.
  const activeRoomRef = useRef(state.ui.activeRoom);
  activeRoomRef.current = state.ui.activeRoom;

  useEffect(() => {
    const container = containerRef.current!;
    let game: Phaser.Game | undefined;
    let disposed = false;

    // Boot only once the container has real dimensions: creating the game
    // during a React commit (or a StrictMode throwaway mount) lets Phaser's
    // renderer initialize against a 0×0 parent and never recover.
    const boot = () => {
      if (disposed || game) return;
      if (container.clientWidth === 0 || container.clientHeight === 0) {
        requestAnimationFrame(boot);
        return;
      }
      officeBridge.playerName = currentUser.displayName;
      officeBridge.statusColors = readStatusColors();
      game = createOfficeGame(container);
    };
    requestAnimationFrame(boot);

    const resizeObserver = new ResizeObserver(() => game?.scale.refresh());
    resizeObserver.observe(container);

    // don't steal WASD/arrow keys while the user types in dashboard inputs
    const onFocusIn = (e: FocusEvent) => {
      if (isTypingTarget(e.target)) officeBridge.setKeyboardEnabled(false);
    };
    const onFocusOut = () => officeBridge.setKeyboardEnabled(true);
    document.addEventListener("focusin", onFocusIn);
    document.addEventListener("focusout", onFocusOut);

    return () => {
      disposed = true;
      document.removeEventListener("focusin", onFocusIn);
      document.removeEventListener("focusout", onFocusOut);
      resizeObserver.disconnect();
      game?.destroy(true);
    };
  }, []);

  useEffect(() => {
    officeBridge.syncAgents(buildPresence(state.agents));
  }, [state.agents]);

  // The scene subscribes to FOCUS_ROOM before it emits GAME_READY (see
  // scenes/Game.ts create()), so this always lands on a live listener —
  // including right after a remount, when the game didn't exist yet at the
  // moment React ran the effect below.
  useEffect(() => {
    const onReady = () => officeBridge.focusRoom(activeRoomRef.current);
    phaserEvents.on(Event.GAME_READY, onReady);
    return () => {
      phaserEvents.off(Event.GAME_READY, onReady);
    };
  }, []);

  useEffect(() => {
    officeBridge.focusRoom(state.ui.activeRoom);
  }, [state.ui.activeRoom]);

  return <div className="office-canvas" ref={containerRef} />;
}
