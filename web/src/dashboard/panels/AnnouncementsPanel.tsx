import { useState } from "react";
import { formatTimeAgo } from "../../shared/format";
import { MegaphoneIcon } from "../../shared/icons";
import { useApp } from "../../shared/store";
import type { AnnouncementCategory } from "../../shared/types";
import { PanelShell } from "./PanelShell";

const CATEGORY_LABEL: Record<AnnouncementCategory, string> = {
  company: "Company",
  update: "Update",
  maintenance: "Maintenance",
};

export function AnnouncementsPanel() {
  const { state, dispatch } = useApp();
  const [composing, setComposing] = useState(false);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [category, setCategory] = useState<AnnouncementCategory>("company");

  const post = () => {
    if (!title.trim()) return;
    dispatch({ type: "addAnnouncement", title: title.trim(), body: body.trim(), category });
    setTitle("");
    setBody("");
    setComposing(false);
  };

  return (
    <PanelShell title="Announcements" subtitle="Mirrored on the in-office board" width={480}>
      {state.announcements.map((ann) => (
        <div className="ann-row" key={ann.id}>
          <span className="ann-icon">
            <MegaphoneIcon width={15} height={15} />
          </span>
          <div className="ann-body">
            <div className="ann-title">{ann.title}</div>
            {ann.body && <div className="ann-text">{ann.body}</div>}
            <div className="ann-time">{formatTimeAgo(ann.createdAt)}</div>
          </div>
          <span className={`badge ${ann.category}`}>{CATEGORY_LABEL[ann.category]}</span>
        </div>
      ))}

      {composing ? (
        <div style={{ marginTop: 16 }}>
          <div className="field">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What's happening?" />
          </div>
          <div className="field">
            <label>Details (optional)</label>
            <textarea rows={2} value={body} onChange={(e) => setBody(e.target.value)} />
          </div>
          <div className="field">
            <label>Category</label>
            <select value={category} onChange={(e) => setCategory(e.target.value as AnnouncementCategory)}>
              <option value="company">Company</option>
              <option value="update">Update</option>
              <option value="maintenance">Maintenance</option>
            </select>
          </div>
          <div className="detail-actions">
            <button className="btn accent" onClick={post} disabled={!title.trim()}>
              Post announcement
            </button>
            <button className="btn" onClick={() => setComposing(false)}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div className="detail-actions">
          <button className="btn accent" onClick={() => setComposing(true)}>
            + New announcement
          </button>
        </div>
      )}
    </PanelShell>
  );
}
