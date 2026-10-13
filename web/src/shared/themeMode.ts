// Light/dark theme switch (UX1). Dark is the default; the choice lives in
// localStorage and is mirrored onto <html data-theme>, which index.css keys
// every colour token off. Built on createDomainStore so the header toggle and
// the gallery stay in sync without a context provider.
import { createDomainStore } from "./domains/createDomainStore";

export type ThemeMode = "dark" | "light";
// BaseCrew starts dark for every fresh install. The brand-specific key also
// prevents a legacy Atrium/CrewMate light preference from overriding the new
// default after the visual rebrand.
const KEY = "basecrew:theme";

function read(): ThemeMode {
  // ?theme=light|dark wins over the stored choice (shareable links, screenshots).
  const q = new URLSearchParams(window.location.search).get("theme");
  if (q === "light" || q === "dark") return q;
  try {
    return localStorage.getItem(KEY) === "light" ? "light" : "dark";
  } catch {
    return "dark";
  }
}

function apply(mode: ThemeMode) {
  document.documentElement.setAttribute("data-theme", mode);
  // theme-color follows the --bg token so the browser chrome matches the page.
  const bg = getComputedStyle(document.documentElement).getPropertyValue("--bg").trim();
  if (bg) document.querySelector('meta[name="theme-color"]')?.setAttribute("content", bg);
}

const store = createDomainStore<ThemeMode>(read());
apply(store.get());

export function setThemeMode(mode: ThemeMode) {
  store.set(mode);
  apply(mode);
  try {
    localStorage.setItem(KEY, mode);
  } catch {
    /* private mode: the choice just doesn't persist */
  }
}

export function useThemeMode() {
  return store.useDomainState();
}
