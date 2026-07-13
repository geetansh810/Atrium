import "./StatusPill.css";

export type PillTone = "neutral" | "info" | "running" | "success" | "warning" | "danger";

interface StatusPillProps {
  label: string;
  tone?: PillTone;
}

// Generic small status pill — used for task statuses, agent health flags
// ("blocked"/paused), and anywhere else a short colored label is needed.
export function StatusPill({ label, tone = "neutral" }: StatusPillProps) {
  return <span className={`status-pill status-pill-${tone}`}>{label}</span>;
}

const TASK_STATUS_TONE: Record<string, PillTone> = {
  queued: "neutral",
  claimed: "running",
  in_progress: "running",
  flagged: "danger",
  pending_review: "warning",
  approved: "success",
  rejected: "danger",
  cancelled: "neutral",
};

export function taskStatusTone(status: string): PillTone {
  return TASK_STATUS_TONE[status] ?? "neutral";
}
