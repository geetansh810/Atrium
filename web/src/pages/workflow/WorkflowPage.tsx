import { useState } from "react";
import { useNavigate } from "react-router";
import { useApp } from "../../shared/store";
import { USE_MOCKS } from "../../shared/config";
import { useAuthSession } from "../../shared/auth";
import { useLlmLogs } from "../../shared/queries";
import { useWorkflowTemplates } from "../../shared/domains/workflows";
import { TASK_LEGEND, taskChainRootId, taskTreeGraph } from "../../shared/graphVocabulary";
import { formatTimeAgo, formatTokens } from "../../shared/format";
import { PageShell } from "../../ui/PageShell";
import { Card } from "../../ui/Card";
import { EmptyState } from "../../ui/EmptyState";
import { StatusPill, taskStatusTone } from "../../ui/StatusPill";
import { Graph } from "../../ui/Graph";
import { GraphLegend } from "../../ui/GraphLegend";
import { AgentLink } from "../../ui/EntityLink";
import "./WorkflowPage.css";

// Node inspector: pick a node in the pipeline, see the task, who worked it,
// and (live API) every LLM call made for it, each a deep link into LLM Logs.
function NodeInspector({ taskId }: { taskId: string }) {
  const { state } = useApp();
  const navigate = useNavigate();
  const companyId = useAuthSession()?.companyId ?? "";
  const task = state.tasks.find((t) => t.id === taskId);
  const agent = task?.assignedAgentId ? state.agents.find((a) => a.id === task.assignedAgentId) : undefined;
  const logs = useLlmLogs(USE_MOCKS ? "" : companyId, { taskId, limit: 20 });

  if (!task) return <EmptyState title="Task not found." />;
  const calls = logs.data?.data ?? [];

  return (
    <div className="workflow-inspector">
      <div className="workflow-inspector-head">
        <h3>{task.title}</h3>
        <StatusPill label={task.status.replace("_", " ")} tone={taskStatusTone(task.status)} />
      </div>
      <dl className="workflow-inspector-facts">
        <div>
          <dt>Worked by</dt>
          <dd>{agent ? <AgentLink agent={agent} /> : "Not claimed yet"}</dd>
        </div>
        <div>
          <dt>Skill</dt>
          <dd>{task.requiredSkill}</dd>
        </div>
        <div>
          <dt>Progress</dt>
          <dd>{task.progress}%</dd>
        </div>
      </dl>
      <div className="detail-actions">
        <button className="btn sm primary" onClick={() => navigate(`/tasks/${task.id}`)}>
          Open task
        </button>
      </div>

      <div className="section-title">LLM calls</div>
      {USE_MOCKS ? (
        <p className="workflow-inspector-note">LLM calls are logged on the live backend only.</p>
      ) : logs.isLoading ? (
        <p className="workflow-inspector-note">Loading…</p>
      ) : calls.length === 0 ? (
        <p className="workflow-inspector-note">No model calls recorded for this task yet.</p>
      ) : (
        <ul className="workflow-inspector-calls">
          {calls.map((c) => (
            <li key={c.id}>
              <button onClick={() => navigate(`/logs?taskId=${task.id}&log=${c.id}`)}>
                <span className="workflow-inspector-call-model">{c.model}</span>
                <span className="workflow-inspector-call-meta">
                  {c.tokensIn == null ? "—" : `${formatTokens(c.tokensIn)} in · ${formatTokens(c.tokensOut ?? 0)} out`} ·{" "}
                  {formatTimeAgo(c.createdAt)}
                </span>
                <StatusPill label={c.status === "ok" ? "OK" : (c.errorKind ?? "Error")} tone={c.status === "ok" ? "success" : "danger"} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// Live pipeline view: the whole decomposed chain at any depth, node-by-node
// inspection down to the individual LLM request (UX4). The Workflow Library
// below is the mock-only template domain (shared/domains/workflows.ts).
export function WorkflowPage() {
  const { state } = useApp();
  const templates = useWorkflowTemplates();
  const [selectedRootId, setSelectedRootId] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  const rootIds = Array.from(
    new Set(
      state.tasks
        .filter((t) => state.tasks.some((child) => child.parentTaskId === t.id))
        .map((t) => taskChainRootId(state.tasks, t.id)),
    ),
  );
  const roots = rootIds.map((id) => state.tasks.find((t) => t.id === id)!).filter(Boolean);
  const root = state.tasks.find((t) => t.id === selectedRootId) ?? roots[0];
  const { layers, edges } = root ? taskTreeGraph(state.tasks, root.id) : { layers: [], edges: [] };
  const inspected = selectedNodeId && layers.flat().some((n) => n.id === selectedNodeId) ? selectedNodeId : root?.id;

  return (
    <PageShell title="Workflow" subtitle="Live task chains. Select a step to inspect its agent and model calls.">
      <Card
        title="Active pipeline"
        actions={
          roots.length > 1 ? (
            <select
              className="workflow-page-select"
              aria-label="Pipeline"
              value={root?.id ?? ""}
              onChange={(e) => {
                setSelectedRootId(e.target.value);
                setSelectedNodeId(null);
              }}
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
          <div className="workflow-split">
            <div className="workflow-graph">
              <GraphLegend entries={TASK_LEGEND} />
              <div className="workflow-graph-scroll">
                <Graph
                  layers={layers}
                  edges={edges}
                  selectedId={inspected}
                  onNodeClick={setSelectedNodeId}
                  ariaLabel="Task pipeline"
                />
              </div>
            </div>
            {inspected && <NodeInspector taskId={inspected} />}
          </div>
        ) : (
          <EmptyState
            title="No multi-step task chains yet"
            description="When an agent splits a task into subtasks, the chain appears here."
          />
        )}
      </Card>

      <Card title="Workflow library">
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
    </PageShell>
  );
}
