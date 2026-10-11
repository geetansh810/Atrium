import type { ReactNode } from "react";
import "./states.css";

// The four non-happy states every data surface has to answer: loading, empty
// (EmptyState), error, and permission-denied. Each says what happened and what
// to do next (UX1 copy rule: no apologies, no vague "something went wrong").

export function Skeleton({ lines = 3, label = "Loading…" }: { lines?: number; label?: string }) {
  return (
    <div className="skeleton" role="status" aria-live="polite" aria-label={label}>
      {Array.from({ length: lines }, (_, i) => (
        <span key={i} className="skeleton-line" style={{ width: `${92 - ((i * 17) % 40)}%` }} />
      ))}
    </div>
  );
}

interface StateProps {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}

export function ErrorState({ title, description, action }: StateProps) {
  return (
    <div className="state-box state-error" role="alert">
      <div className="state-title">{title}</div>
      {description && <div className="state-desc">{description}</div>}
      {action && <div className="state-action">{action}</div>}
    </div>
  );
}

export function ForbiddenState({ title, description, action }: StateProps) {
  return (
    <div className="state-box state-forbidden">
      <div className="state-title">{title}</div>
      {description && <div className="state-desc">{description}</div>}
      {action && <div className="state-action">{action}</div>}
    </div>
  );
}
