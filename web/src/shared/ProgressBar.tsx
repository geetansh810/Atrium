import "./ProgressBar.css";

interface ProgressBarProps {
  value: number; // 0–100
  color?: string; // CSS color/var override; defaults to the brand gradient
  height?: number;
  // In-flight work gets a moving sheen. Reserve for genuinely live tasks
  // (Mission Control hero, a working agent's card) — static measures like
  // budget burn stay still.
  live?: boolean;
}

export function ProgressBar({ value, color, height = 6, live = false }: ProgressBarProps) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <span
      className="progress-track"
      style={{ height, borderRadius: height / 2 }}
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <span
        className={`progress-fill${live ? " progress-fill-live" : ""}`}
        style={{ width: `${clamped}%`, borderRadius: height / 2, background: color }}
      />
    </span>
  );
}
