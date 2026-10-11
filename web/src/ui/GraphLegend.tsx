import type { GraphNodeStatus } from "./Graph";
import { NODE_STATUS_COLOR } from "../shared/graphVocabulary";
import "./GraphLegend.css";

export function GraphLegend({ entries }: { entries: { status: GraphNodeStatus; label: string }[] }) {
  return (
    <ul className="graph-legend" aria-label="Legend">
      {entries.map((e) => (
        <li key={e.status}>
          <span className="graph-legend-swatch" style={{ background: NODE_STATUS_COLOR[e.status] }} />
          {e.label}
        </li>
      ))}
    </ul>
  );
}
