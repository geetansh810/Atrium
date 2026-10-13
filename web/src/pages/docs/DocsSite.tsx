import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation, useParams } from "react-router";
import { BrandMark } from "../../shared/BrandMark";
import { setThemeMode, useThemeMode } from "../../shared/themeMode";
import { DEFAULT_SLUG, DOCS, findPage, pageNeighbors } from "./docsNav";
import "./docs.css";

function DocsHeader({ onMenu }: { onMenu: () => void }) {
  const mode = useThemeMode();
  return (
    <header className="dx-header">
      <div className="dx-header-in">
        <button className="dx-menu" onClick={onMenu} aria-label="Toggle navigation">
          <span />
          <span />
          <span />
        </button>
        <Link className="dx-brand" to="/">
          <BrandMark compact />
          <span className="dx-header-tag">Docs</span>
        </Link>
        <div className="dx-header-auth">
          <button
            className="dx-theme"
            type="button"
            onClick={() => setThemeMode(mode === "dark" ? "light" : "dark")}
          >
            {mode === "dark" ? "Light" : "Dark"}
          </button>
          <Link className="dx-header-home" to="/">
            Home
          </Link>
          <Link className="dx-signup" to="/signup">
            Sign up
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

function DocPageView() {
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
      <DocsHeader onMenu={() => setMenuOpen((v) => !v)} />
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

// Public, signed in or not. App.tsx mounts this above the auth gate.
export function DocsSite() {
  const location = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);

  return (
    <div className="docs">
      <Routes>
        <Route path="/docs" element={<Navigate to={`/docs/${DEFAULT_SLUG}`} replace />} />
        <Route path="/docs/:slug" element={<DocPageView />} />
        <Route path="/docs/*" element={<Navigate to={`/docs/${DEFAULT_SLUG}`} replace />} />
      </Routes>
    </div>
  );
}
