import type { ReactNode } from "react";
import type { PillTone } from "./StatusPill";
import "./Timeline.css";

export interface TimelineItem {
  id: string;
  label: ReactNode;
  timestamp: string; // pre-formatted display string
  tone?: PillTone;
}

// Vertical audit-trail list — first real consumer is the task detail timeline
// (MF-3), reading straight off the new Task.events contract extension.
export function Timeline({ items }: { items: TimelineItem[] }) {
  return (
    <ol className="timeline">
      {items.map((item) => (
        <li key={item.id} className="timeline-item">
          <span className={`timeline-dot timeline-dot-${item.tone ?? "neutral"}`} />
          <div className="timeline-item-body">
            <div className="timeline-item-label">{item.label}</div>
            <div className="timeline-item-timestamp">{item.timestamp}</div>
          </div>
        </li>
      ))}
    </ol>
  );
}
