import type { ReactNode } from "react";
import { CloseIcon } from "../../shared/icons";
import { useApp } from "../../shared/store";
import "./panels.css";

interface PanelShellProps {
  title: string;
  subtitle?: string;
  width?: number;
  noPad?: boolean;
  children: ReactNode;
}

// Overlay panel docked over the office canvas — the chrome every
// reference-2 surface shares (title, close, scrollable body).
export function PanelShell({ title, subtitle, width = 430, noPad = false, children }: PanelShellProps) {
  const { dispatch } = useApp();
  const close = () => dispatch({ type: "closePanel" });

  return (
    <div className="panel-overlay" onClick={close}>
      <section
        className="panel"
        style={{ width }}
        onClick={(e) => e.stopPropagation()}
        aria-label={title}
      >
        <header className="panel-head">
          <div>
            <h2>{title}</h2>
            {subtitle && <div className="panel-subtitle">{subtitle}</div>}
          </div>
          <button className="panel-close" onClick={close} aria-label="Close panel">
            <CloseIcon />
          </button>
        </header>
        <div className={`panel-body${noPad ? " no-pad" : ""}`}>{children}</div>
      </section>
    </div>
  );
}
