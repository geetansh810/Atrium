import type { ReactNode } from "react";
import { useEffect, useRef } from "react";
import { CloseIcon } from "../shared/icons";
import "./Drawer.css";

interface DrawerProps {
  title: string;
  subtitle?: string;
  width?: number;
  noPad?: boolean;
  onClose: () => void;
  children: ReactNode;
}

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

// Full-height right-edge slide-over. Generalized from panels/PanelShell.tsx's
// chrome (title/subtitle/close/scrollable body) for surfaces that feel like a
// persistent side panel rather than a modal dialog — first user: the chat
// drawer (ChatPanel). Future consumers: TaskDrawer (MF-3), FocusPod (MF-4).
export function Drawer({ title, subtitle, width = 420, noPad = false, onClose, children }: DrawerProps) {
  const sectionRef = useRef<HTMLElement>(null);

  // Focus the drawer on open, trap Tab within it, and close on Escape — the
  // three things a right-edge overlay needs to behave like a real dialog
  // instead of just looking like one.
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    sectionRef.current?.focus();

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (e.key !== "Tab" || !sectionRef.current) return;
      const focusable = sectionRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      previouslyFocused?.focus();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="drawer-overlay" onClick={onClose}>
      <section
        ref={sectionRef}
        className="drawer"
        style={{ width }}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
      >
        <header className="drawer-head">
          <div>
            <h2>{title}</h2>
            {subtitle && <div className="drawer-subtitle">{subtitle}</div>}
          </div>
          <button className="drawer-close" onClick={onClose} aria-label="Close">
            <CloseIcon />
          </button>
        </header>
        <div className={`drawer-body${noPad ? " no-pad" : ""}`}>{children}</div>
      </section>
    </div>
  );
}
