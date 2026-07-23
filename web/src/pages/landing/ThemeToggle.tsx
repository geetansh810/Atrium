import type { EnterpriseTheme } from "./useEnterpriseTheme";

// A single control that flips the enterprise surfaces between light and dark.
// Shows the icon of the mode it will switch *to*, and labels itself for
// screen readers by the action, not the current state.
export function ThemeToggle({ theme, onToggle }: { theme: EnterpriseTheme; onToggle: () => void }) {
  const toDark = theme === "light";
  return (
    <button
      type="button"
      className="e-theme-toggle"
      onClick={onToggle}
      aria-label={toDark ? "Switch to dark mode" : "Switch to light mode"}
      title={toDark ? "Dark mode" : "Light mode"}
    >
      {toDark ? (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M21 12.8A8.5 8.5 0 1 1 11.2 3a6.6 6.6 0 0 0 9.8 9.8z" />
        </svg>
      ) : (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
        </svg>
      )}
    </button>
  );
}
