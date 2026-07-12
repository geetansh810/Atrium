import type { AgentStatus } from "./types";

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

export function StatusDot({ status, size = 8 }: { status: AgentStatus; size?: number }) {
  return (
    <span
      style={{
        display: "inline-block",
        width: size,
        height: size,
        borderRadius: "50%",
        background: STATUS_VAR[status],
        flexShrink: 0,
      }}
      aria-label={STATUS_LABEL[status]}
    />
  );
}
