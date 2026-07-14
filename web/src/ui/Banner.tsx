import type { ReactNode } from "react";
import "./Banner.css";

export type BannerTone = "info" | "warning" | "danger";

interface BannerProps {
  tone?: BannerTone;
  title: string;
  description?: ReactNode;
}

// Persistent inline alert (not a dismissable Toast) — first use: the
// stalled-tasks warning on Mission Control / Tasks (see
// selectors.findStalledTasks), surfacing the "queued tasks aging while agents
// sit idle" trap that's easy to miss otherwise.
export function Banner({ tone = "warning", title, description }: BannerProps) {
  return (
    <div className={`banner banner-${tone}`}>
      <div className="banner-title">{title}</div>
      {description && <div className="banner-description">{description}</div>}
    </div>
  );
}
