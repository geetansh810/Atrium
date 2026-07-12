interface ProgressBarProps {
  value: number; // 0–100
  color?: string; // CSS color/var; defaults to status green
  height?: number;
}

export function ProgressBar({ value, color = "var(--status-online)", height = 6 }: ProgressBarProps) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <span
      style={{
        display: "block",
        width: "100%",
        height,
        borderRadius: height / 2,
        background: "var(--border-subtle)",
        overflow: "hidden",
      }}
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <span
        style={{
          display: "block",
          height: "100%",
          width: `${clamped}%`,
          borderRadius: height / 2,
          background: color,
          transition: "width 0.3s ease",
        }}
      />
    </span>
  );
}
