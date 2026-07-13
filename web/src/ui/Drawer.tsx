import type { ReactNode } from "react";
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

// Full-height right-edge slide-over. Generalized from panels/PanelShell.tsx's
// chrome (title/subtitle/close/scrollable body) for surfaces that feel like a
// persistent side panel rather than a modal dialog — first user: the chat
// drawer (ChatPanel). Future consumers: TaskDrawer (MF-3), FocusPod (MF-4).
export function Drawer({ title, subtitle, width = 420, noPad = false, onClose, children }: DrawerProps) {
  return (
    <div className="drawer-overlay" onClick={onClose}>
      <section
        className="drawer"
        style={{ width }}
        onClick={(e) => e.stopPropagation()}
        aria-label={title}
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
