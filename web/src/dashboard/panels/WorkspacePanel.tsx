import { useState } from "react";
import { ProgressBar } from "../../shared/ProgressBar";
import { PRIORITY_LABEL } from "../../shared/format";
import { skillColor } from "../../shared/skillColor";
import { useApp } from "../../shared/store";
import type { Task, TaskStatus } from "../../shared/types";
import { PanelShell } from "./PanelShell";

type Tab = "my" | "assigned" | "completed";

const TABS: { key: Tab; label: string }[] = [
  { key: "my", label: "My Tasks" },
  { key: "assigned", label: "Assigned to Me" },
  { key: "completed", label: "Completed" },
];

const ACTIVE: TaskStatus[] = ["queued", "claimed", "in_progress", "flagged", "pending_review"];
// "Assigned to Me" for the human = everything waiting on human action
const WAITING_ON_ME: TaskStatus[] = ["pending_review", "flagged"];

function tabFilter(tab: Tab, task: Task): boolean {
  if (tab === "my") return ACTIVE.includes(task.status);
  if (tab === "assigned") return WAITING_ON_ME.includes(task.status);
  return task.status === "approved";
}

const STATUS_TEXT: Record<TaskStatus, string> = {
  queued: "Queued",
  claimed: "Claimed",
  in_progress: "In Progress",
  flagged: "Flagged",
  pending_review: "Pending Review",
  approved: "Approved",
  rejected: "Rejected",
  cancelled: "Cancelled",
};

export function WorkspacePanel() {
  const { state, dispatch } = useApp();
  const [tab, setTab] = useState<Tab>("my");
  const agentById = new Map(state.agents.map((a) => [a.id, a]));

  const list = state.tasks.filter((t) => tabFilter(tab, t));
  const selected =
    state.tasks.find((t) => t.id === state.ui.selectedTaskId && tabFilter(tab, t)) ?? list[0];

  const doneCount = selected?.subtasks.filter((s) => s.state === "done").length ?? 0;
  const agent = selected?.assignedAgentId ? agentById.get(selected.assignedAgentId) : undefined;
  const hasFlow =
    selected != null &&
    (selected.parentTaskId != null || state.tasks.some((t) => t.parentTaskId === selected.id));

  return (
    <PanelShell title="My Workspace" width={760} noPad>
      <div className="workspace-grid">
        <div className="workspace-list">
          <div className="workspace-tabs">
            {TABS.map(({ key, label }) => (
              <button
                key={key}
                className={`workspace-tab${tab === key ? " active" : ""}`}
                onClick={() => setTab(key)}
              >
                {label}
              </button>
            ))}
          </div>

          {list.length === 0 && <div className="empty-note">Nothing here.</div>}
          {list.map((task) => {
            const taskAgent = task.assignedAgentId ? agentById.get(task.assignedAgentId) : undefined;
            return (
              <div
                key={task.id}
                className={`task-row${selected?.id === task.id ? " selected" : ""}`}
                onClick={() => dispatch({ type: "openPanel", panel: "workspace", taskId: task.id })}
              >
                <span className="task-row-icon" style={{ background: skillColor(task.requiredSkill) }}>
                  {task.requiredSkill.slice(0, 1)}
                </span>
                <span className="task-row-info">
                  <span className="task-row-title">{task.title}</span>
                  <span className="task-row-meta">
                    {taskAgent?.name ?? "Unassigned"}
                    {task.etaMinutes != null ? ` · ETA ${task.etaMinutes}m` : ""}
                  </span>
                </span>
                <span className={`status-badge ${task.status}`}>{STATUS_TEXT[task.status]}</span>
              </div>
            );
          })}
        </div>

        <div className="workspace-detail">
          {!selected ? (
            <div className="empty-note">Select a task.</div>
          ) : (
            <>
              <h3 className="detail-title">{selected.title}</h3>
              <p className="detail-desc">{selected.description}</p>

              <div className="detail-meta-row">
                <span className={`status-badge ${selected.status}`}>{STATUS_TEXT[selected.status]}</span>
                <span>{agent?.name ?? "Unassigned"}</span>
                <span>Priority: {PRIORITY_LABEL[selected.priority] ?? selected.priority}</span>
                <span>Skill: {selected.requiredSkill}</span>
              </div>

              <ProgressBar value={selected.progress} />
              <div className="profile-task-pct">
                {selected.progress}%
                {selected.etaMinutes != null ? ` · ETA: ${selected.etaMinutes} minutes` : ""}
              </div>

              {selected.subtasks.length > 0 && (
                <>
                  <div className="section-title">
                    Subtasks ({doneCount}/{selected.subtasks.length})
                  </div>
                  {selected.subtasks.map((sub) => (
                    <div
                      key={sub.id}
                      className={`subtask-row ${sub.state}`}
                      onClick={() =>
                        dispatch({ type: "toggleSubtask", taskId: selected.id, subtaskId: sub.id })
                      }
                    >
                      <span className="subtask-check">
                        {sub.state === "done" ? "✓" : sub.state === "doing" ? <span className="subtask-dot" /> : ""}
                      </span>
                      <span className="subtask-label">{sub.label}</span>
                    </div>
                  ))}
                </>
              )}

              {selected.flagReason && (
                <>
                  <div className="section-title">Flagged — needs a human</div>
                  <div className="artifact-box flag">{selected.flagReason}</div>
                </>
              )}

              {selected.artifact && (
                <>
                  <div className="section-title">Output ({selected.artifact.kind})</div>
                  <div className="artifact-box">{selected.artifact.content}</div>
                </>
              )}

              {selected.feedback && (
                <>
                  <div className="section-title">Review feedback</div>
                  <div className="artifact-box feedback">{selected.feedback}</div>
                </>
              )}

              <div className="detail-actions">
                {hasFlow && (
                  <button
                    className="btn"
                    onClick={() => dispatch({ type: "openPanel", panel: "flow", taskId: selected.id })}
                  >
                    View Task Flow
                  </button>
                )}
                {(selected.status === "pending_review" || selected.status === "flagged") && (
                  <button
                    className="btn accent"
                    onClick={() => dispatch({ type: "openPanel", panel: "approvals", taskId: selected.id })}
                  >
                    Review in Approvals
                  </button>
                )}
                {agent && (
                  <button
                    className="btn"
                    onClick={() => dispatch({ type: "openPanel", panel: "profile", agentId: agent.id })}
                  >
                    View {agent.name}
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </PanelShell>
  );
}
