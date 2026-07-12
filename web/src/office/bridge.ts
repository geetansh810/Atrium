// Replaces upstream `services/Network.ts` as the single doorway into the
// Phaser world. Today React pushes mock-store state through it; at M2.4c the
// office-realtime Colyseus client plugs in here and the scenes don't change.
import { Event, phaserEvents } from "./events";
import type { ResolvedSeat } from "./officeLayout";
import type { AgentStatus } from "../shared/types";

export interface AgentPresence {
  id: string;
  name: string;
  texture: string; // spriteKey: adam | ash | lucy | nancy
  status: AgentStatus;
  activity: string;
  seat: ResolvedSeat | null; // null = not in the office (offline)
}

class OfficeBridge {
  agents: AgentPresence[] = [];
  playerName = "";
  // Filled from the dashboard's CSS --status-* variables before boot, so the
  // canvas matches the UI theme without hardcoding colors here.
  statusColors: Partial<Record<AgentStatus, number>> = {};

  syncAgents(agents: AgentPresence[]) {
    this.agents = agents;
    phaserEvents.emit(Event.AGENTS_UPDATED, agents);
  }

  focusRoom(roomLabel: string) {
    phaserEvents.emit(Event.FOCUS_ROOM, roomLabel);
  }

  setKeyboardEnabled(enabled: boolean) {
    phaserEvents.emit(Event.KEYBOARD_ENABLED, enabled);
  }

  onAgentClicked(handler: (agentId: string) => void) {
    phaserEvents.on(Event.AGENT_CLICKED, handler);
    return () => {
      phaserEvents.off(Event.AGENT_CLICKED, handler);
    };
  }
}

export const officeBridge = new OfficeBridge();
