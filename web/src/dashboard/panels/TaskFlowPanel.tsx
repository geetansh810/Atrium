import { Avatar } from "../../shared/Avatar";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import type { Task } from "../../shared/types";
import { PanelShell } from "./PanelShell";

function nodeClass(task: Task): string {
  if (task.status === "approved") return "flow-node done";
  if (task.status === "in_progress" || task.status === "claimed") return "flow-node active";
  return "flow-node";
}

// Parent → children graph per GET /tasks/{id}/flow (reference-2 "Task Flow").
export function TaskFlowPanel() {
  const { state, dispatch } = useApp();
  const nav = useAppNav();
  const agentById = new Map(state.agents.map((a) => [a.id, a]));

  const roots = state.tasks.filter((t) =>
    state.tasks.some((child) => child.parentTaskId === t.id),
  );
  const selectedRaw = state.tasks.find((t) => t.id === state.ui.selectedTaskId);
  const root = selectedRaw
    ? (state.tasks.find((t) => t.id === selectedRaw.parentTaskId) ?? selectedRaw)
    : roots[0];

  if (!root) {
    return (
      <PanelShell title="Task Flow" width={460}>
        <div className="empty-note">No multi-step task chains yet.</div>
      </PanelShell>
    );
  }

  const children = state.tasks.filter((t) => t.parentTaskId === root.id);
  const rootAgent = root.assignedAgentId ? agentById.get(root.assignedAgentId) : undefined;
  const allDone = children.length > 0 && children.every((c) => c.status === "approved");

  return (
    <PanelShell title={`Task Flow: ${root.title}`} width={520}>
      {roots.length > 1 && (
        <div className="field">
          <label>Flow</label>
          <select
            value={root.id}
            onChange={(e) => dispatch({ type: "openPanel", panel: "flow", taskId: e.target.value })}
          >
            {roots.map((r) => (
              <option key={r.id} value={r.id}>
                {r.title}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="flow-canvas">
        <div className="flow-node">
          <span className="flow-node-title">User Request</span>
          <span className="flow-node-sub">{root.title}</span>
        </div>
        <div className="flow-edge" />

        <div
          className={nodeClass(root)}
          style={{ cursor: "pointer" }}
          onClick={() => nav.openTask(root.id)}
        >
          {rootAgent && <Avatar name={rootAgent.name} seed={rootAgent.id} size={26} />}
          <span className="flow-node-title">{rootAgent?.name ?? "Unassigned"}</span>
          <span className="flow-node-sub">{root.progress}% · {root.status.replace("_", " ")}</span>
        </div>

        {children.length > 0 && (
          <>
            <div className="flow-edge" />
            <div className="flow-children">
              {children.map((child) => {
                const agent = child.assignedAgentId ? agentById.get(child.assignedAgentId) : undefined;
                return (
                  <div className="flow-child" key={child.id}>
                    <div
                      className={nodeClass(child)}
                      style={{ cursor: "pointer" }}
                      onClick={() => nav.openTask(child.id)}
                    >
                      {agent && <Avatar name={agent.name} seed={agent.id} size={26} />}
                      <span className="flow-node-title">{agent?.name ?? "Unassigned"}</span>
                      <span className="flow-node-sub">{child.title}</span>
                      <span className={`status-badge ${child.status}`}>
                        {child.status.replace("_", " ")}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="flow-edge" />
            <div className={`flow-node${allDone ? " done" : ""}`}>
              <span className="flow-node-title">{allDone ? "✅ Report Generated" : "Result"}</span>
              <span className="flow-node-sub">{allDone ? "All steps approved" : "Waiting on steps above"}</span>
            </div>
          </>
        )}
      </div>
    </PanelShell>
  );
}
