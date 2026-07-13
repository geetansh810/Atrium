import type { ReactNode } from "react";
import "./PageShell.css";

interface PageShellProps {
  title: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}

// Consistent page header + scrollable body chrome. Generalizes the ad hoc
// header markup EmployeesPage/PagePlaceholder wrote by hand pre-MF-2 — later
// milestones migrate those onto this instead of duplicating the CSS.
export function PageShell({ title, subtitle, actions, children }: PageShellProps) {
  return (
    <div className="page-shell">
      <header className="page-shell-head">
        <div>
          <h1>{title}</h1>
          {subtitle && <p>{subtitle}</p>}
        </div>
        {actions && <div className="page-shell-actions">{actions}</div>}
      </header>
      <div className="page-shell-body">{children}</div>
    </div>
  );
}
