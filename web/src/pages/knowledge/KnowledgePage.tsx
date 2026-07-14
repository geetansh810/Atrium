import { useMemo, useState } from "react";
import { USE_MOCKS } from "../../shared/config";
import { formatTimeAgo } from "../../shared/format";
import { useKnowledgeDocs } from "../../shared/domains/knowledge";
import { Drawer } from "../../ui/Drawer";
import { StatusPill } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import "./KnowledgePage.css";

// Net-new domain, no backend equivalent yet (MF-5) — docs/embeddings schema
// exists in core-api (15 §4) but no endpoints are wired up.
export function KnowledgePage() {
  const docs = useKnowledgeDocs();
  const [query, setQuery] = useState("");
  const [openId, setOpenId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return docs;
    return docs.filter(
      (doc) =>
        doc.title.toLowerCase().includes(q) ||
        doc.excerpt.toLowerCase().includes(q) ||
        doc.tags.some((tag) => tag.toLowerCase().includes(q)),
    );
  }, [docs, query]);

  const openDoc = docs.find((d) => d.id === openId);

  return (
    <div className="knowledge-page">
      <header className="knowledge-page-head">
        <div>
          <h1>Knowledge</h1>
          <p>Shared docs and process notes.</p>
        </div>
        {!USE_MOCKS && <StatusPill label="Sample data" tone="warning" />}
      </header>

      <input
        className="knowledge-search"
        placeholder="Search docs or tags…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      {filtered.length === 0 ? (
        <EmptyState title="No docs match that search." />
      ) : (
        <div className="knowledge-list">
          {filtered.map((doc) => (
            <button key={doc.id} className="knowledge-card" onClick={() => setOpenId(doc.id)}>
              <div className="knowledge-card-title">{doc.title}</div>
              <p className="knowledge-card-excerpt">{doc.excerpt}</p>
              <div className="knowledge-card-foot">
                <div className="chip-row">
                  {doc.tags.map((tag) => (
                    <span className="chip" key={tag}>
                      {tag}
                    </span>
                  ))}
                </div>
                <span className="knowledge-card-time">Updated {formatTimeAgo(doc.updatedAt)}</span>
              </div>
            </button>
          ))}
        </div>
      )}

      {openDoc && (
        <Drawer title={openDoc.title} subtitle={`Updated ${formatTimeAgo(openDoc.updatedAt)}`} onClose={() => setOpenId(null)}>
          <div className="chip-row" style={{ marginBottom: 12 }}>
            {openDoc.tags.map((tag) => (
              <span className="chip" key={tag}>
                {tag}
              </span>
            ))}
          </div>
          <p className="knowledge-drawer-body">{openDoc.body}</p>
        </Drawer>
      )}
    </div>
  );
}
