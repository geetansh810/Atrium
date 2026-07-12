import { PlusIcon } from "../shared/icons";
import { Avatar } from "../shared/Avatar";
import { StatusDot } from "../shared/StatusDot";
import { skillColor } from "../shared/skillColor";
import { useApp } from "../shared/store";
import "./RightRail.css";

const RAIL_STATUSES = new Set(["queued", "claimed", "in_progress", "flagged"]);

export function RightRail() {
  const { state, dispatch } = useApp();
  const agentById = new Map(state.agents.map((a) => [a.id, a]));
  const railTasks = state.tasks.filter(
    (t) => t.parentTaskId === null && RAIL_STATUSES.has(t.status),
  );

  return (
    <aside className="right-rail">
      <section className="rail-section">
        <div className="rail-section-head">
          <h3>My Tasks</h3>
          <button
            className="rail-add-btn"
            aria-label="Add task"
            onClick={() => dispatch({ type: "setNewTaskOpen", open: true })}
          >
            <PlusIcon />
          </button>
        </div>
        <ul className="rail-task-list">
          {railTasks.map((task) => {
            const agent = task.assignedAgentId ? agentById.get(task.assignedAgentId) : undefined;
            return (
              <li
                className="rail-task-row clickable"
                key={task.id}
                onClick={() => dispatch({ type: "openPanel", panel: "workspace", taskId: task.id })}
              >
                <span className="rail-task-icon" style={{ background: skillColor(task.requiredSkill) }} />
                <span className="rail-task-info">
                  <span className="rail-task-title">{task.title}</span>
                  <span className="rail-task-meta">{agent?.name ?? "Unassigned"}</span>
                </span>
                <span className="rail-task-eta">
                  {task.status === "flagged"
                    ? "⚑ flagged"
                    : task.etaMinutes != null
                      ? `ETA: ${task.etaMinutes}m`
                      : "—"}
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      <section className="rail-section">
        <div className="rail-section-head">
          <h3>Agent Status</h3>
        </div>
        <ul className="rail-status-list">
          {state.agents.map((agent) => (
            <li
              className="rail-status-row clickable"
              key={agent.id}
              onClick={() => dispatch({ type: "openPanel", panel: "profile", agentId: agent.id })}
            >
              <Avatar name={agent.name} seed={agent.id} size={28} />
              <span className="rail-status-info">
                <span className="rail-status-name">{agent.name}</span>
                <span className="rail-status-activity">{agent.currentActivity}</span>
              </span>
              <StatusDot status={agent.status} />
            </li>
          ))}
        </ul>
      </section>
    </aside>
  );
}
