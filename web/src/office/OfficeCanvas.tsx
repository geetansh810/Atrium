// React mount for the forked SkyOffice canvas. Everything flows one way:
// store state -> bridge -> Phaser (office = projection); the only signal
// coming back is "an avatar was clicked", which opens the profile panel.
import { useEffect, useRef } from "react";
import type Phaser from "phaser";
import { createOfficeGame } from "./createGame";
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
  const { state, dispatch } = useApp();
  const containerRef = useRef<HTMLDivElement>(null);
  const firstRoomRender = useRef(true);

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

  useEffect(() => {
    if (firstRoomRender.current) {
      firstRoomRender.current = false;
      return;
    }
    officeBridge.focusRoom(state.ui.activeRoom);
  }, [state.ui.activeRoom]);

  useEffect(() => {
    return officeBridge.onAgentClicked((agentId) => {
      dispatch({ type: "openPanel", panel: "profile", agentId });
    });
  }, [dispatch]);

  return <div className="office-canvas" ref={containerRef} />;
}
