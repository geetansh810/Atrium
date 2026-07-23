import { useMemo, useState } from "react";
import { USE_MOCKS } from "../../shared/config";
import { formatTimeAgo } from "../../shared/format";
import { useKnowledgeDocs as useMockKnowledgeDocs } from "../../shared/domains/knowledge";
import { useAuthSession } from "../../shared/auth";
import { ApiError } from "../../shared/api";
import {
  useKnowledgeDocs as useApiKnowledgeDocs,
  useIngestKnowledgeMutation,
  useArchiveKnowledgeMutation,
} from "../../shared/queries";
import { Drawer } from "../../ui/Drawer";
import { StatusPill } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import "./KnowledgePage.css";

// Mock mode has no ingest endpoint to call — this stays the original MF-5
// read-only fixture browser (search + drawer over the static JSON docs).
function MockKnowledgePage() {
  const docs = useMockKnowledgeDocs();
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

// Real ingest/list/archive (16 §3, M-KN1) finally gets a UI — the backend has
// shipped POST/GET/DELETE /companies/{id}/knowledge since session 17, but no
// frontend ever called them until now (MF-5 shipped this page mock-only,
// before M-KN1 existed). Ingested content is never returned back by the API
// (it's chunked straight into embeddings, no doc-level body column), so the
// drawer here shows metadata only, not a body preview — an honest limit,
// not a bug.
function ApiKnowledgePage() {
  const companyId = useAuthSession()?.companyId ?? "";
  const { data: docs = [], isLoading } = useApiKnowledgeDocs(companyId);
  const ingest = useIngestKnowledgeMutation(companyId);
  const archive = useArchiveKnowledgeMutation(companyId);

  const [query, setQuery] = useState("");
  const [openId, setOpenId] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [sourceUri, setSourceUri] = useState("");
  const [error, setError] = useState<string | null>(null);

  const active = docs.filter((d) => d.status !== "archived");
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return active;
    return active.filter((doc) => doc.title.toLowerCase().includes(q));
  }, [active, query]);

  const openDoc = active.find((d) => d.id === openId);
  const canSubmit = title.trim() && content.trim() && !ingest.isPending;

  const submit = () => {
    if (!canSubmit) return;
    setError(null);
    ingest.mutate(
      { title: title.trim(), content: content.trim(), sourceUri: sourceUri.trim() || undefined },
      {
        onSuccess: () => {
          setTitle("");
          setContent("");
          setSourceUri("");
          setFormOpen(false);
        },
        onError: (err) => setError(err instanceof ApiError ? err.message : "Failed to add document."),
      },
    );
  };

  return (
    <div className="knowledge-page">
      <header className="knowledge-page-head">
        <div>
          <h1>Knowledge</h1>
          <p>Shared docs and process notes, recalled into agent prompts by role.</p>
        </div>
        <button className="btn primary sm" onClick={() => setFormOpen((v) => !v)}>
          {formOpen ? "Cancel" : "+ Add document"}
        </button>
      </header>

      {formOpen && (
        <div className="knowledge-form">
          <div className="field">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Brand style guide" autoFocus />
          </div>
          <div className="field">
            <label>Content (plain text or markdown)</label>
            <textarea rows={6} value={content} onChange={(e) => setContent(e.target.value)} placeholder="Paste the document text…" />
          </div>
          <div className="field">
            <label>Source URL (optional)</label>
            <input value={sourceUri} onChange={(e) => setSourceUri(e.target.value)} placeholder="https://…" />
          </div>
          {error && <p className="knowledge-form-error">{error}</p>}
          <div className="knowledge-form-actions">
            <button className="btn primary sm" disabled={!canSubmit} onClick={submit}>
              {ingest.isPending ? "Adding…" : "Add document"}
            </button>
          </div>
        </div>
      )}

      <input
        className="knowledge-search"
        placeholder="Search docs…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      {isLoading ? null : filtered.length === 0 ? (
        <EmptyState
          title={active.length === 0 ? "No knowledge docs yet." : "No docs match that search."}
          description={active.length === 0 ? "Add one above, then attach it to a role so agents can recall it." : undefined}
        />
      ) : (
        <div className="knowledge-list">
          {filtered.map((doc) => (
            <button key={doc.id} className="knowledge-card" onClick={() => setOpenId(doc.id)}>
              <div className="knowledge-card-title">{doc.title}</div>
              <div className="knowledge-card-foot">
                <div className="chip-row">
                  <span className="chip">{doc.mime}</span>
                </div>
                <span className="knowledge-card-time">Added {formatTimeAgo(doc.createdAt)}</span>
              </div>
            </button>
          ))}
        </div>
      )}

      {openDoc && (
        <Drawer title={openDoc.title} subtitle={`Added ${formatTimeAgo(openDoc.createdAt)}`} onClose={() => setOpenId(null)}>
          <div className="chip-row" style={{ marginBottom: 12 }}>
            <span className="chip">{openDoc.mime}</span>
            <StatusPill label={openDoc.status} tone={openDoc.status === "active" ? "success" : "neutral"} />
          </div>
          {openDoc.sourceUri && (
            <p className="knowledge-drawer-source">
              Source: <a href={openDoc.sourceUri} target="_blank" rel="noreferrer">{openDoc.sourceUri}</a>
            </p>
          )}
          <p className="knowledge-drawer-body">
            Content isn't shown here — it's chunked and embedded for agent recall, not stored as a
            single browsable body. Attach this doc to a role (Role Definitions) to make it recallable.
          </p>
          <div className="detail-actions">
            <button
              className="btn danger sm"
              disabled={archive.isPending}
              onClick={() => {
                archive.mutate(openDoc.id);
                setOpenId(null);
              }}
            >
              Archive
            </button>
          </div>
        </Drawer>
      )}
    </div>
  );
}

// Net-new domain — mock docs/embeddings schema is fixture-only (MF-5); the
// real core-api endpoints (M-KN1, 16 §3) are wired here in API mode only.
export function KnowledgePage() {
  return USE_MOCKS ? <MockKnowledgePage /> : <ApiKnowledgePage />;
}
