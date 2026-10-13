// Content atoms shared by every docs page. Pages compose these instead of raw
// markup so headings, tables, callouts and code all render consistently and the
// right-rail "On this page" list can anchor to <Section> ids.
import type { ReactNode } from "react";

export function Lead({ children }: { children: ReactNode }) {
  return <p className="doc-lead">{children}</p>;
}

export function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section className="doc-section" id={id}>
      <h2>
        <a href={`#${id}`} className="doc-anchor" aria-label={title}>
          #
        </a>
        {title}
      </h2>
      {children}
    </section>
  );
}

export function P({ children }: { children: ReactNode }) {
  return <p className="doc-p">{children}</p>;
}

export function Ul({ children }: { children: ReactNode }) {
  return <ul className="doc-ul">{children}</ul>;
}

export function Li({ children }: { children: ReactNode }) {
  return <li>{children}</li>;
}

export function Code({ children }: { children: ReactNode }) {
  return <code className="doc-code">{children}</code>;
}

export function CodeBlock({ children, label }: { children: ReactNode; label?: string }) {
  return (
    <div className="doc-codeblock">
      {label && <div className="doc-codeblock-label">{label}</div>}
      <pre>
        <code>{children}</code>
      </pre>
    </div>
  );
}

type Tone = "note" | "warn" | "ok";
export function Callout({ tone = "note", title, children }: { tone?: Tone; title?: string; children: ReactNode }) {
  return (
    <div className={`doc-callout doc-callout-${tone}`}>
      {title && <div className="doc-callout-title">{title}</div>}
      <div className="doc-callout-body">{children}</div>
    </div>
  );
}

export function DocTable({ head, rows }: { head: string[]; rows: ReactNode[][] }) {
  return (
    <div className="doc-table-wrap">
      <table className="doc-table">
        <thead>
          <tr>
            {head.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function CardGrid({ children }: { children: ReactNode }) {
  return <div className="doc-cardgrid">{children}</div>;
}

export function DocCard({ tag, title, children }: { tag?: string; title: string; children: ReactNode }) {
  return (
    <div className="doc-card">
      {tag && <div className="doc-card-tag">{tag}</div>}
      <h4>{title}</h4>
      <p>{children}</p>
    </div>
  );
}
