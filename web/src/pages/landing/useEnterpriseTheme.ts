import { useCallback, useLayoutEffect, useState } from "react";

// The dashboard body is a fixed dark gradient (index.css). The marketing and
// docs surfaces are light by default but user-switchable, so while one is
// mounted we tag <html> with the scope class and the chosen theme, and CSS
// repaints from there — both are removed on unmount so navigating back into the
// app (or mock mode) restores the dark dashboard exactly.
export type EnterpriseTheme = "light" | "dark";
const STORAGE_KEY = "atrium-theme";

function initialTheme(): EnterpriseTheme {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === "light" || saved === "dark") return saved;
  } catch {
    /* private mode / disabled storage — fall through to system preference */
  }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function useEnterpriseTheme() {
  const [theme, setThemeState] = useState<EnterpriseTheme>(initialTheme);

  // Scope class lives for the whole time an enterprise surface is mounted.
  useLayoutEffect(() => {
    const root = document.documentElement;
    root.classList.add("enterprise-scope");
    return () => {
      root.classList.remove("enterprise-scope");
      root.removeAttribute("data-enterprise-theme");
    };
  }, []);

  // The theme attribute updates on every toggle without churning the scope class.
  useLayoutEffect(() => {
    document.documentElement.setAttribute("data-enterprise-theme", theme);
  }, [theme]);

  const setTheme = useCallback((next: EnterpriseTheme) => {
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* ignore write failures */
    }
    setThemeState(next);
  }, []);

  const toggle = useCallback(() => {
    setTheme(theme === "dark" ? "light" : "dark");
  }, [theme, setTheme]);

  return { theme, setTheme, toggle };
}
