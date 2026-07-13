import "./Graph.css";

export type GraphNodeStatus = "pending" | "running" | "completed" | "failed";

export interface GraphNode {
  id: string;
  label: string;
  sublabel?: string;
  status: GraphNodeStatus;
}

export interface GraphEdge {
  from: string;
  to: string;
}

interface GraphProps {
  // Nodes grouped into columns (layers) left-to-right — e.g. [ [root], [children...] ].
  layers: GraphNode[][];
  edges: GraphEdge[];
  onNodeClick?: (id: string) => void;
  compact?: boolean;
}

const NODE_W = 132;
const NODE_H = 44;

// Hand-rolled SVG layered tree — no charting library. Mini form here backs
// Mission Control's workflow preview (MF-2); the full task DAG (MF-5) and org
// chart (MF-4) are bigger instances of the same component.
export function Graph({ layers, edges, onNodeClick, compact = false }: GraphProps) {
  const colGap = compact ? 64 : 96;
  const rowGap = compact ? 16 : 24;
  const maxRows = Math.max(1, ...layers.map((l) => l.length));
  const width = layers.length * NODE_W + Math.max(0, layers.length - 1) * colGap;
  const height = maxRows * NODE_H + Math.max(0, maxRows - 1) * rowGap;

  const positions = new Map<string, { x: number; y: number }>();
  layers.forEach((layer, colIdx) => {
    const colHeight = layer.length * NODE_H + Math.max(0, layer.length - 1) * rowGap;
    const yOffset = (height - colHeight) / 2;
    layer.forEach((node, rowIdx) => {
      positions.set(node.id, {
        x: colIdx * (NODE_W + colGap),
        y: yOffset + rowIdx * (NODE_H + rowGap),
      });
    });
  });

  return (
    <svg
      className={`graph${compact ? " graph-compact" : ""}`}
      viewBox={`0 0 ${width} ${height}`}
      width="100%"
      height={height}
      role="img"
      aria-label="Workflow graph"
    >
      <g className="graph-edges">
        {edges.map((edge) => {
          const from = positions.get(edge.from);
          const to = positions.get(edge.to);
          if (!from || !to) return null;
          const x1 = from.x + NODE_W;
          const y1 = from.y + NODE_H / 2;
          const x2 = to.x;
          const y2 = to.y + NODE_H / 2;
          const midX = (x1 + x2) / 2;
          return (
            <path
              key={`${edge.from}-${edge.to}`}
              d={`M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`}
              className="graph-edge"
            />
          );
        })}
      </g>
      <g className="graph-nodes">
        {layers.flat().map((node) => {
          const pos = positions.get(node.id);
          if (!pos) return null;
          return (
            <g
              key={node.id}
              transform={`translate(${pos.x}, ${pos.y})`}
              className={`graph-node graph-node-${node.status}${onNodeClick ? " graph-node-clickable" : ""}`}
              onClick={onNodeClick ? () => onNodeClick(node.id) : undefined}
            >
              <rect width={NODE_W} height={NODE_H} rx={8} />
              <text x={10} y={18} className="graph-node-label">
                {node.label.length > 18 ? `${node.label.slice(0, 17)}…` : node.label}
              </text>
              {node.sublabel && (
                <text x={10} y={33} className="graph-node-sublabel">
                  {node.sublabel}
                </text>
              )}
            </g>
          );
        })}
      </g>
    </svg>
  );
}
