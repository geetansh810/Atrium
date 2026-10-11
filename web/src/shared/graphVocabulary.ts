// One status vocabulary for every graph-shaped view (UX4): the workflow DAG,
// the org chart cards and the Reports status bars all colour by the same four
// node states, drawn from the --node-* tokens. Views differ only in the words
// they print in their legend.
import type { Agent, Task } from "./types";
import type { GraphEdge, GraphNode, GraphNodeStatus } from "../ui/Graph";

export const NODE_STATUS_COLOR: Record<GraphNodeStatus, string> = {
  pending: "var(--node-pending)",
  running: "var(--node-running)",
  completed: "var(--node-completed)",
  failed: "var(--node-failed)",
};

export function taskNodeStatus(status: string): GraphNodeStatus {
  if (status === "approved") return "completed";
  if (status === "flagged" || status === "rejected") return "failed";
  if (status === "claimed" || status === "in_progress") return "running";
  return "pending";
}

export function agentNodeStatus(agent: Agent): GraphNodeStatus {
  if (agent.paused) return "pending";
  if (agent.status === "working") return "running";
  if (agent.status === "online" || agent.status === "in_focus") return "completed";
  return "pending";
}

export const TASK_LEGEND: { status: GraphNodeStatus; label: string }[] = [
  { status: "pending", label: "Queued or in review" },
  { status: "running", label: "Being worked" },
  { status: "completed", label: "Approved" },
  { status: "failed", label: "Flagged or sent back" },
];

export const AGENT_LEGEND: { status: GraphNodeStatus; label: string }[] = [
  { status: "running", label: "Working" },
  { status: "completed", label: "Ready" },
  { status: "pending", label: "Away, paused or offline" },
];

// Whole subtree under `rootId` as left-to-right layers (depth = column), so a
// decomposed chain of any depth renders, not just parent + children.
export function taskTreeGraph(tasks: Task[], rootId: string): { layers: GraphNode[][]; edges: GraphEdge[] } {
  const byParent = new Map<string, Task[]>();
  for (const t of tasks) {
    if (!t.parentTaskId) continue;
    const list = byParent.get(t.parentTaskId) ?? [];
    list.push(t);
    byParent.set(t.parentTaskId, list);
  }
  const root = tasks.find((t) => t.id === rootId);
  if (!root) return { layers: [], edges: [] };

  const toNode = (t: Task): GraphNode => ({
    id: t.id,
    label: t.title,
    sublabel: `${t.progress}% · ${t.status.replace("_", " ")}`,
    status: taskNodeStatus(t.status),
  });
  const layers: GraphNode[][] = [];
  const edges: GraphEdge[] = [];
  const seen = new Set<string>([root.id]);
  let level: Task[] = [root];
  while (level.length > 0) {
    layers.push(level.map(toNode));
    const next: Task[] = [];
    for (const parent of level) {
      for (const child of byParent.get(parent.id) ?? []) {
        if (seen.has(child.id)) continue;
        seen.add(child.id);
        edges.push({ from: parent.id, to: child.id });
        next.push(child);
      }
    }
    level = next;
  }
  return { layers, edges };
}

// Root of the chain any task belongs to (walks parentTaskId up).
export function taskChainRootId(tasks: Task[], taskId: string): string {
  const byId = new Map(tasks.map((t) => [t.id, t]));
  let current = byId.get(taskId);
  const guard = new Set<string>();
  while (current?.parentTaskId && byId.has(current.parentTaskId) && !guard.has(current.id)) {
    guard.add(current.id);
    current = byId.get(current.parentTaskId);
  }
  return current?.id ?? taskId;
}
