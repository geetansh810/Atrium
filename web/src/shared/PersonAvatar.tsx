import { Avatar } from "./Avatar";
import { StatusDot, STATUS_LABEL } from "./StatusDot";
import type { AgentStatus } from "./types";
import "./PersonAvatar.css";

interface PersonAvatarProps {
  name: string;
  seed: string;
  status: AgentStatus;
  size?: number;
}

// Avatar with a small presence dot at the corner — one compact signal for
// "who is this, and are they active" instead of a separate labeled status
// row. The shared minimal identity mark for the org tree and employee grid.
export function PersonAvatar({ name, seed, status, size = 34 }: PersonAvatarProps) {
  return (
    <span className="person-avatar" title={STATUS_LABEL[status]}>
      <Avatar name={name} seed={seed} size={size} />
      <span className="person-avatar-dot">
        <StatusDot status={status} size={8} />
      </span>
    </span>
  );
}
