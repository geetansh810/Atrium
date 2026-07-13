import type { ReactNode } from "react";
import "./Card.css";

interface CardProps {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

// Generic bordered surface — the base building block every widget on
// Mission Control (and later Projects/Reports) sits inside.
export function Card({ title, actions, children, className }: CardProps) {
  return (
    <section className={`card${className ? ` ${className}` : ""}`}>
      {(title || actions) && (
        <div className="card-head">
          {title && <h2 className="card-title">{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </div>
      )}
      <div className="card-body">{children}</div>
    </section>
  );
}
