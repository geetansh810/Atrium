import { useEffect, useState } from "react";
import { useApp } from "../../shared/store";
import { useNotifications } from "../../shared/notifications";
import type { NotificationKind } from "../../shared/notifications";
import { formatTimeAgo } from "../../shared/format";
import type { AnnouncementCategory } from "../../shared/types";
import { StatusPill } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import { PlusIcon } from "../../shared/icons";
import "./NotificationsPage.css";

const CATEGORY_LABEL: Record<AnnouncementCategory, string> = {
  company: "Company",
  update: "Update",
  maintenance: "Maintenance",
};

const FILTERS: { key: NotificationKind | "all"; label: string }[] = [
  { key: "all", label: "All" },
  { key: "task", label: "Task activity" },
  { key: "announcement", label: "Announcements" },
];

export function NotificationsPage() {
  const { dispatch } = useApp();
  const { items, unreadCount, markAllRead } = useNotifications();
  const [filter, setFilter] = useState<NotificationKind | "all">("all");
  const [composing, setComposing] = useState(false);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [category, setCategory] = useState<AnnouncementCategory>("company");

  // Viewing the page counts as reading it — mirrors the header bell's own
  // signal for what "unread" means (no server-side read receipts to sync).
  useEffect(() => {
    markAllRead();
  }, [markAllRead]);

  const post = () => {
    if (!title.trim()) return;
    dispatch({ type: "addAnnouncement", title: title.trim(), body: body.trim(), category });
    setTitle("");
    setBody("");
    setCategory("company");
    setComposing(false);
  };

  const visible = filter === "all" ? items : items.filter((i) => i.kind === filter);

  return (
    <div className="notifications-page">
      <header className="notifications-page-head">
        <div>
          <h1>Notifications</h1>
          <p>
            Task activity across the company, plus announcements.
            {unreadCount > 0 ? ` ${unreadCount} unread when you opened this page.` : ""}
          </p>
        </div>
        <button className="btn primary" onClick={() => setComposing((v) => !v)}>
          <PlusIcon width={13} height={13} /> New announcement
        </button>
      </header>

      {composing && (
        <div className="notifications-composer">
          <div className="field">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What's the headline?" autoFocus />
          </div>
          <div className="field">
            <label>Body (optional)</label>
            <textarea rows={2} value={body} onChange={(e) => setBody(e.target.value)} />
          </div>
          <div className="field-row">
            <div className="field">
              <label>Category</label>
              <select value={category} onChange={(e) => setCategory(e.target.value as AnnouncementCategory)}>
                {(Object.keys(CATEGORY_LABEL) as AnnouncementCategory[]).map((c) => (
                  <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="detail-actions">
            <button className="btn" onClick={() => setComposing(false)}>Cancel</button>
            <button className="btn accent" disabled={!title.trim()} onClick={post}>Post</button>
          </div>
        </div>
      )}

      <div className="notifications-filters">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            className={`notifications-filter${filter === f.key ? " active" : ""}`}
            onClick={() => setFilter(f.key)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {visible.length === 0 ? (
        <EmptyState title="Nothing here yet." description="Task activity and announcements will show up as they happen." />
      ) : (
        <ul className="notifications-list">
          {visible.map((item) => (
            <li key={item.id} className="notifications-row">
              <StatusPill label={item.category ? CATEGORY_LABEL[item.category] : "Activity"} tone={item.tone} />
              <div className="notifications-row-body">
                <div className="notifications-row-title">{item.title}</div>
                {item.detail && <div className="notifications-row-detail">{item.detail}</div>}
              </div>
              <div className="notifications-row-time">{formatTimeAgo(item.createdAt)}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
