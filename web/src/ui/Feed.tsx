import { Avatar } from "../shared/Avatar";
import { formatTimeAgo } from "../shared/format";
import { EmptyState } from "./EmptyState";
import "./Feed.css";

export interface FeedEntry {
  id: string;
  agentName: string | null;
  text: string;
  createdAt: string;
}

// Compact live activity list — Mission Control's window into task_events
// across every task. `agentName` renders an avatar; system/user events fall
// back to a plain dot.
export function Feed({ items, emptyLabel = "No activity yet." }: { items: FeedEntry[]; emptyLabel?: string }) {
  if (items.length === 0) return <EmptyState title={emptyLabel} />;
  return (
    <ul className="feed">
      {items.map((item) => (
        <li key={item.id} className="feed-item">
          {item.agentName ? (
            <Avatar name={item.agentName} seed={item.agentName} size={22} />
          ) : (
            <span className="feed-item-dot" aria-hidden />
          )}
          <span className="feed-item-text">{item.text}</span>
          <span className="feed-item-time">{formatTimeAgo(item.createdAt)}</span>
        </li>
      ))}
    </ul>
  );
}
