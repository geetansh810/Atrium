import type { ReactNode } from "react";
import "./StatCard.css";

interface StatCardProps {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "neutral" | "warn" | "danger";
}

// A single KPI tile — Mission Control's top row is a grid of these.
export function StatCard({ label, value, hint, tone = "neutral" }: StatCardProps) {
  return (
    <div className={`stat-card stat-card-${tone}`}>
      <div className="stat-card-label">{label}</div>
      <div className="stat-card-value">{value}</div>
      {hint && <div className="stat-card-hint">{hint}</div>}
    </div>
  );
}
