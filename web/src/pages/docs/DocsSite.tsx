import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation, useParams } from "react-router";
import { PRODUCT_NAME } from "../../shared/theme";
import { useEnterpriseTheme, type EnterpriseTheme } from "../landing/useEnterpriseTheme";
import { ThemeToggle } from "../landing/ThemeToggle";
import { DEFAULT_SLUG, DOCS, findPage, pageNeighbors } from "./docsNav";
import "../landing/LandingPage.css";
import "./docs.css";

function DocsHeader({
  onMenu,
  theme,
  onToggle,
}: {
  onMenu: () => void;
  theme: EnterpriseTheme;
  onToggle: () => void;
}) {
  return (
    <header className="dx-header">
      <div className="dx-header-in">
        <button className="dx-menu" onClick={onMenu} aria-label="Toggle navigation">
          <span />
          <span />
          <span />
        </button>
        <Link className="e-brand" to="/">
          <span className="e-brand-mark" aria-hidden="true" />
          <b>{PRODUCT_NAME}</b>
          <span className="dx-header-tag">Docs</span>
        </Link>
        <div className="dx-header-auth">
          <ThemeToggle theme={theme} onToggle={onToggle} />
          <Link className="dx-header-home" to="/">
            Home
          </Link>
          <Link className="e-btn e-btn-primary e-btn-sm" to="/signup">
            Start free
          </Link>
        </div>
      </div>
    </header>
  );
}

function DocsSidebar({ activeSlug, onNavigate }: { activeSlug: string; onNavigate: () => void }) {
  return (
    <nav className="dx-side" aria-label="Documentation">
      {DOCS.map((cat) => (
        <div className="dx-side-group" key={cat.label}>
          <div className="dx-side-label">{cat.label}</div>
          <ul>
            {cat.pages.map((p) => (
              <li key={p.slug}>
                <Link
                  to={`/docs/${p.slug}`}
                  className={p.slug === activeSlug ? "dx-side-link active" : "dx-side-link"}
                  onClick={onNavigate}
                >
                  {p.title}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function DocPageView({ theme, onToggle }: { theme: EnterpriseTheme; onToggle: () => void }) {
  const { slug } = useParams();
  const page = findPage(slug);
  const { prev, next } = pageNeighbors(page.slug);
  const { Component } = page;
  const [menuOpen, setMenuOpen] = useState(false);

  // Reset scroll and close the mobile drawer whenever the page changes.
  useEffect(() => {
    window.scrollTo(0, 0);
    setMenuOpen(false);
  }, [slug]);

  return (
    <>
      <DocsHeader onMenu={() => setMenuOpen((v) => !v)} theme={theme} onToggle={onToggle} />
      <div className={menuOpen ? "dx-shell menu-open" : "dx-shell"}>
        <aside className="dx-side-wrap">
          <DocsSidebar activeSlug={page.slug} onNavigate={() => setMenuOpen(false)} />
        </aside>
        <button
          className="dx-scrim"
          aria-label="Close navigation"
          onClick={() => setMenuOpen(false)}
        />

        <main className="dx-main">
          <article className="dx-article">
            <div className="dx-breadcrumb">Docs / {page.title}</div>
            <h1 className="dx-title">{page.title}</h1>
            <Component />

            <nav className="dx-pager">
              {prev ? (
                <Link className="dx-pager-link prev" to={`/docs/${prev.slug}`}>
                  <span>Previous</span>
                  <b>{prev.title}</b>
                </Link>
              ) : (
                <span />
              )}
              {next ? (
                <Link className="dx-pager-link next" to={`/docs/${next.slug}`}>
                  <span>Next</span>
                  <b>{next.title}</b>
                </Link>
              ) : (
                <span />
              )}
            </nav>
          </article>

          <aside className="dx-toc">
            <div className="dx-toc-label">On this page</div>
            <ul>
              {page.headings.map((h) => (
                <li key={h.id}>
                  <a href={`#${h.id}`}>{h.label}</a>
                </li>
              ))}
            </ul>
          </aside>
        </main>
      </div>
    </>
  );
}

// Docs is a public surface reachable signed-in or signed-out; App.tsx mounts it
// outside the auth gate. It carries its own light enterprise theme.
export function DocsSite() {
  const { theme, toggle } = useEnterpriseTheme();
  const location = useLocation();

  // React Router doesn't restore scroll between docs pages on its own.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);

  return (
    <div className="enterprise docs">
      <Routes>
        <Route path="/docs" element={<Navigate to={`/docs/${DEFAULT_SLUG}`} replace />} />
        <Route path="/docs/:slug" element={<DocPageView theme={theme} onToggle={toggle} />} />
        <Route path="/docs/*" element={<Navigate to={`/docs/${DEFAULT_SLUG}`} replace />} />
      </Routes>
    </div>
  );
}
