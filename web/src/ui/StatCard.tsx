import { useEffect, useRef, useState, type ReactNode } from "react";
import "./StatCard.css";

// Animates a numeric KPI from its previous value to the new one (0 → value on
// first mount). Non-numeric values ("3/6", "31h30m") render as-is.
function useCountUp(target: number, duration = 650): number {
  const [display, setDisplay] = useState(0);
  const fromRef = useRef(0);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      fromRef.current = target;
      setDisplay(target);
      return;
    }
    const from = fromRef.current;
    if (from === target) {
      setDisplay(target);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplay(Math.round(from + (target - from) * eased));
      if (t < 1) {
        raf = requestAnimationFrame(tick);
      } else {
        fromRef.current = target;
      }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, duration]);

  return display;
}

interface StatCardProps {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "neutral" | "warn" | "danger";
}

// A single KPI tile — Mission Control's top row is a grid of these.
export function StatCard({ label, value, hint, tone = "neutral" }: StatCardProps) {
  const isNumber = typeof value === "number";
  const animated = useCountUp(isNumber ? value : 0);
  return (
    <div className={`stat-card stat-card-${tone}`}>
      <div className="stat-card-label">{label}</div>
      <div className="stat-card-value">{isNumber ? animated : value}</div>
      {hint && <div className="stat-card-hint">{hint}</div>}
    </div>
  );
}
