// Maps Atrium locationKeys / agent statuses to coordinates on the forked
// SkyOffice map (web/public/assets/map/map.json). Seat positions are the
// centers of that map's Tiled "Chair" objects (center = objX+16, objY-32),
// so avatars sit exactly where a human player could sit.
//
// This table is the seed data for the future `office_layout` table (M2.4c);
// office-realtime will serve the same shape.

import { sittingShiftData } from "./characters/Player";
import type { AgentStatus } from "../shared/types";

type Direction = "up" | "down" | "left" | "right";

interface Seat {
  x: number; // chair/stand-spot center
  y: number;
  dir: Direction;
  sit: boolean; // false = standing spot (lobby)
}

export interface ResolvedSeat {
  seatId: string;
  x: number; // final avatar position (sit shift already applied)
  y: number;
  anim: string; // texture-less anim suffix, e.g. "sit_up" / "idle_down"
  depth: number | null; // fixed depth while seated; null = follow y
}

const SEATS: Record<string, Seat> = {
  // Workstations — right wing, chairs paired with computer desks
  desk_1: { x: 1008, y: 544, dir: "up", sit: true },
  desk_2: { x: 1104, y: 544, dir: "up", sit: true },
  desk_3: { x: 1200, y: 544, dir: "up", sit: true },
  desk_4: { x: 1008, y: 800, dir: "up", sit: true },
  desk_5: { x: 1200, y: 800, dir: "up", sit: true },
  desk_6: { x: 1104, y: 704, dir: "down", sit: true },
  desk_7: { x: 1104, y: 448, dir: "down", sit: true },
  desk_8: { x: 1200, y: 448, dir: "down", sit: true },
  desk_9: { x: 1008, y: 704, dir: "down", sit: true },
  // Meeting room — top-middle conference table
  meeting_a1: { x: 656, y: 96, dir: "down", sit: true },
  meeting_a2: { x: 688, y: 96, dir: "down", sit: true },
  meeting_a3: { x: 720, y: 96, dir: "down", sit: true },
  // Meeting room beta — long classroom row, left-middle
  meeting_b1: { x: 240, y: 384, dir: "down", sit: true },
  meeting_b2: { x: 272, y: 384, dir: "down", sit: true },
  meeting_b3: { x: 304, y: 384, dir: "down", sit: true },
  meeting_b4: { x: 496, y: 384, dir: "down", sit: true },
  meeting_b5: { x: 528, y: 384, dir: "down", sit: true },
  meeting_b6: { x: 560, y: 384, dir: "down", sit: true },
  meeting_b7: { x: 592, y: 384, dir: "down", sit: true },
  // Focus pods — quiet library tables, bottom-left
  focus_pod_1: { x: 336, y: 608, dir: "down", sit: true },
  focus_pod_2: { x: 400, y: 608, dir: "down", sit: true },
  focus_pod_3: { x: 464, y: 608, dir: "down", sit: true },
  focus_pod_4: { x: 336, y: 704, dir: "up", sit: true },
  focus_pod_5: { x: 400, y: 704, dir: "up", sit: true },
  focus_pod_6: { x: 464, y: 704, dir: "up", sit: true },
  focus_pod_7: { x: 528, y: 672, dir: "left", sit: true },
  focus_pod_8: { x: 272, y: 672, dir: "right", sit: true },
  // Cafe — sofas by the vending machine
  cafe_1: { x: 464, y: 250, dir: "right", sit: true },
  cafe_2: { x: 464, y: 296, dir: "right", sit: true },
  cafe_3: { x: 560, y: 250, dir: "left", sit: true },
  cafe_4: { x: 560, y: 296, dir: "left", sit: true },
  // Help desk — lone chair, top-right (flagged tasks, future)
  help_desk_1: { x: 976, y: 160, dir: "left", sit: true },
  // Lobby — standing spots around the spawn point
  lobby_1: { x: 660, y: 470, dir: "down", sit: false },
  lobby_2: { x: 750, y: 470, dir: "down", sit: false },
  lobby_3: { x: 705, y: 545, dir: "down", sit: false },
  lobby_4: { x: 630, y: 530, dir: "right", sit: false },
  lobby_5: { x: 780, y: 530, dir: "left", sit: false },
};

const ZONES: Record<string, string[]> = {
  workstations: ["desk_1", "desk_2", "desk_3", "desk_4", "desk_5", "desk_6", "desk_7", "desk_8", "desk_9"],
  meeting_room_alpha: ["meeting_a1", "meeting_a2", "meeting_a3"],
  meeting_room_beta: ["meeting_b1", "meeting_b2", "meeting_b3", "meeting_b4", "meeting_b5", "meeting_b6", "meeting_b7"],
  focus_pods: ["focus_pod_1", "focus_pod_2", "focus_pod_3", "focus_pod_4", "focus_pod_5", "focus_pod_6", "focus_pod_7", "focus_pod_8"],
  cafe: ["cafe_1", "cafe_2", "cafe_3", "cafe_4"],
  help_desk: ["help_desk_1"],
  lobby: ["lobby_1", "lobby_2", "lobby_3", "lobby_4", "lobby_5"],
};

// Position IS status: when a locationKey is missing or already taken, the
// agent falls back to the zone its status maps to.
const STATUS_ZONES: Record<AgentStatus, string[]> = {
  online: ["lobby"],
  working: ["workstations"],
  in_meeting: ["meeting_room_alpha", "meeting_room_beta"],
  in_focus: ["focus_pods"],
  away: ["cafe"],
  offline: [], // not rendered
};

export const PLAYER_SPAWN = { x: 705, y: 500 };

// Camera anchors for the Sidebar room shortcuts (keys = room labels).
export const ROOM_CAMERA: Record<string, { x: number; y: number }> = {
  Lobby: { x: 705, y: 500 },
  Workstations: { x: 1090, y: 640 },
  "Meeting Rooms": { x: 620, y: 240 },
  Cafe: { x: 470, y: 270 },
  "Focus Pods": { x: 390, y: 660 },
  "Server Room": { x: 330, y: 290 },
  Rooftop: { x: 640, y: 96 },
};

function toResolved(seatId: string): ResolvedSeat {
  const seat = SEATS[seatId];
  if (!seat.sit) {
    return { seatId, x: seat.x, y: seat.y, anim: `idle_${seat.dir}`, depth: null };
  }
  const [dx, dy, dDepth] = sittingShiftData[seat.dir];
  return {
    seatId,
    x: seat.x + dx,
    y: seat.y + dy,
    anim: `sit_${seat.dir}`,
    depth: seat.y + dDepth,
  };
}

/**
 * Deterministic seat assignment for the whole roster: direct locationKey
 * claims win (first agent by id on a conflict), then zone keys, then the
 * status-zone fallback. Stable as long as statuses don't change.
 */
export function resolveSeats(
  agents: { id: string; locationKey: string; status: AgentStatus }[],
): Map<string, ResolvedSeat | null> {
  const sorted = [...agents].sort((a, b) => a.id.localeCompare(b.id));
  const taken = new Set<string>();
  const result = new Map<string, ResolvedSeat | null>();

  const claimFromZones = (zoneKeys: string[]): string | null => {
    for (const zone of zoneKeys) {
      for (const seatId of ZONES[zone] ?? []) {
        if (!taken.has(seatId)) return seatId;
      }
    }
    return null;
  };

  // Pass 1: exact seat keys (e.g. "desk_2")
  for (const agent of sorted) {
    if (agent.status === "offline") {
      result.set(agent.id, null);
      continue;
    }
    if (SEATS[agent.locationKey] && !taken.has(agent.locationKey)) {
      taken.add(agent.locationKey);
      result.set(agent.id, toResolved(agent.locationKey));
    }
  }

  // Pass 2: zone keys (e.g. "cafe") and fallbacks
  for (const agent of sorted) {
    if (result.has(agent.id)) continue;
    const seatId =
      claimFromZones(ZONES[agent.locationKey] ? [agent.locationKey] : []) ??
      claimFromZones(STATUS_ZONES[agent.status]) ??
      claimFromZones(["lobby"]);
    if (seatId) {
      taken.add(seatId);
      result.set(agent.id, toResolved(seatId));
    } else {
      result.set(agent.id, null); // office is full — should not happen with mock roster
    }
  }

  return result;
}
