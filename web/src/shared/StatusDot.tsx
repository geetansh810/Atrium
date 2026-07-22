import type { CSSProperties } from "react";
import type { AgentStatus } from "./types";
import "./StatusDot.css";

const STATUS_VAR: Record<AgentStatus, string> = {
  online: "var(--status-online)",
  working: "var(--status-working)",
  in_meeting: "var(--status-in-meeting)",
  in_focus: "var(--status-in-focus)",
  away: "var(--status-away)",
  offline: "var(--status-offline)",
};

export const STATUS_LABEL: Record<AgentStatus, string> = {
  online: "Online",
  working: "Working",
  in_meeting: "In Meeting",
  in_focus: "In Focus",
  away: "Away",
  offline: "Offline",
};

interface StatusDotProps {
  status: AgentStatus;
  size?: number;
  // Live state breathes. Defaults on for "working" — an agent actively doing
  // something right now — and off for everything else, so the pulse stays a
  // signal rather than ambient noise.
  pulse?: boolean;
}

export function StatusDot({ status, size = 8, pulse }: StatusDotProps) {
  const live = pulse ?? status === "working";
  return (
    <span
      className={`status-dot${live ? " status-dot-live" : ""}`}
      style={{ width: size, height: size, "--dot-color": STATUS_VAR[status] } as CSSProperties}
      aria-label={STATUS_LABEL[status]}
    />
  );
}
