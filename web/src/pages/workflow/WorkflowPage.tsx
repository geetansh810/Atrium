import { useState } from "react";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { USE_MOCKS } from "../../shared/config";
import { useWorkflowTemplates } from "../../shared/domains/workflows";
import { Card } from "../../ui/Card";
import { EmptyState } from "../../ui/EmptyState";
import { StatusPill } from "../../ui/StatusPill";
import { Graph } from "../../ui/Graph";
import type { GraphEdge, GraphNode, GraphNodeStatus } from "../../ui/Graph";
import "./WorkflowPage.css";

function nodeStatus(status: string): GraphNodeStatus {
  if (status === "approved") return "completed";
  if (status === "flagged" || status === "rejected") return "failed";
  if (status === "claimed" || status === "in_progress") return "running";
  return "pending";
}

// Live pipeline view (MF-5) — port + upgrade of the classic Task Flow panel:
// same parent → child task chain, now rendered via ui/Graph (its first real,
// non-mini DAG consumer) with live node status colors, click-through to
// TaskDrawer. The "Workflow Library" below is a separate mock-only domain
// (shared/domains/workflows.ts) — named templates, not live instances.
export function WorkflowPage() {
  const { state } = useApp();
  const nav = useAppNav();
  const templates = useWorkflowTemplates();
  const [selectedRootId, setSelectedRootId] = useState<string | null>(null);

  const roots = state.tasks.filter((t) => state.tasks.some((child) => child.parentTaskId === t.id));
  const root = state.tasks.find((t) => t.id === selectedRootId) ?? roots[0];
  const children = root ? state.tasks.filter((t) => t.parentTaskId === root.id) : [];

  const rootNode: GraphNode | undefined = root
    ? {
        id: root.id,
        label: root.title,
        sublabel: `${root.progress}% · ${root.status.replace("_", " ")}`,
        status: nodeStatus(root.status),
      }
    : undefined;
  const childNodes: GraphNode[] = children.map((t) => ({
    id: t.id,
    label: t.title,
    sublabel: `${t.progress}%`,
    status: nodeStatus(t.status),
  }));
  const layers = rootNode ? (childNodes.length ? [[rootNode], childNodes] : [[rootNode]]) : [];
  const edges: GraphEdge[] = rootNode ? children.map((t) => ({ from: rootNode.id, to: t.id })) : [];

  return (
    <div className="workflow-page">
      <header className="workflow-page-head">
        <h1>Workflow</h1>
        <p>Live parent → child task pipelines.</p>
      </header>

      <Card
        title="Active Pipeline"
        actions={
          roots.length > 1 ? (
            <select
              className="workflow-page-select"
              value={root?.id ?? ""}
              onChange={(e) => setSelectedRootId(e.target.value)}
            >
              {roots.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.title}
                </option>
              ))}
            </select>
          ) : undefined
        }
      >
        {layers.length ? (
          <Graph layers={layers} edges={edges} onNodeClick={nav.openTask} />
        ) : (
          <EmptyState
            title="No multi-step task chains yet."
            description="Create a task with subtasks to see it pipeline through here."
          />
        )}
      </Card>

      <Card title="Workflow Library" actions={!USE_MOCKS ? <StatusPill label="Sample data" tone="warning" /> : undefined}>
        <div className="workflow-template-list">
          {templates.map((tpl) => (
            <div className="workflow-template-card" key={tpl.id}>
              <div className="workflow-template-name">{tpl.name}</div>
              <p className="workflow-template-desc">{tpl.description}</p>
              <div className="workflow-template-steps">
                {tpl.steps.map((step, i) => (
                  <span className="workflow-template-step" key={step}>
                    {i + 1}. {step}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
