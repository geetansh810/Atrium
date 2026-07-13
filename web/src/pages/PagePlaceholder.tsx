import type { ReactNode } from "react";
import "./PagePlaceholder.css";

interface PlaceholderAction {
  label: string;
  onClick: () => void;
}

interface PagePlaceholderProps {
  title: string;
  subtitle: string;
  description?: ReactNode;
  actions?: PlaceholderAction[];
}

// Generic stand-in for a routed page whose real build is a later MF
// milestone. Each is a thin per-route wrapper (see pages/*/*.tsx) so
// replacing one with a real page is a same-path file swap, not a rewire.
export function PagePlaceholder({ title, subtitle, description, actions }: PagePlaceholderProps) {
  return (
    <div className="page-placeholder">
      <h1>{title}</h1>
      <p className="page-placeholder-subtitle">{subtitle}</p>
      {description && <div className="page-placeholder-description">{description}</div>}
      {actions && actions.length > 0 && (
        <div className="page-placeholder-actions">
          {actions.map((action) => (
            <button key={action.label} className="btn" onClick={action.onClick}>
              {action.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
