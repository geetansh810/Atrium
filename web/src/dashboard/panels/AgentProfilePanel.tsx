import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { StatusDot, STATUS_LABEL } from "../../shared/StatusDot";
import { formatClockTime, formatFocusTime, formatTimeAgo } from "../../shared/format";
import { useApp } from "../../shared/store";
import { PanelShell } from "./PanelShell";

const ACTIVE_STATUSES = new Set(["queued", "claimed", "in_progress", "flagged"]);

export function AgentProfilePanel() {
  const { state, dispatch } = useApp();
  const agent = state.agents.find((a) => a.id === state.ui.selectedAgentId);
  if (!agent) return null;

  const stats = state.agentStats[agent.id] ?? { tasksCompleted: 0, successRate: 100, focusMinutes: 0 };
  const currentTasks = state.tasks.filter(
    (t) => t.assignedAgentId === agent.id && ACTIVE_STATUSES.has(t.status),
  );
  const feed = state.activity.filter((e) => e.agentId === agent.id).slice(0, 8);
  const manager = state.agents.find((a) => a.id === agent.managerAgentId);
  const dm = state.channels.find((c) => c.kind === "dm" && c.agentId === agent.id);

  return (
    <PanelShell title="Agent Profile" width={430}>
      <div className="profile-header">
        <Avatar name={agent.name} seed={agent.id} size={56} square />
        <div className="profile-header-info">
          <span className="profile-name">{agent.name}</span>
          <span className="profile-meta">
            <StatusDot status={agent.status} /> {STATUS_LABEL[agent.status]}
          </span>
          <span className="profile-meta">{agent.roleTitle}</span>
          <span className="profile-meta">
            Joined {formatTimeAgo(agent.joinedAt)}
            {manager ? ` · Reports to ${manager.name}` : ""}
          </span>
        </div>
      </div>

      <div className="profile-stats">
        <div className="stat-card">
          <div className="stat-card-label">Tasks Completed</div>
          <div className="stat-card-value">{stats.tasksCompleted}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">Success Rate</div>
          <div className="stat-card-value">{stats.successRate}%</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">Focus Time</div>
          <div className="stat-card-value">{formatFocusTime(stats.focusMinutes)}</div>
        </div>
      </div>

      <div className="section-title">Current Tasks</div>
      {currentTasks.length === 0 && <div className="empty-note">No active tasks.</div>}
      {currentTasks.map((task) => (
        <div
          key={task.id}
          className="profile-task"
          onClick={() => dispatch({ type: "openPanel", panel: "workspace", taskId: task.id })}
        >
          <div className="profile-task-top">
            <span>{task.title}</span>
            <span className="profile-task-eta">
              {task.etaMinutes != null ? `ETA: ${task.etaMinutes}m` : ""}
            </span>
          </div>
          <ProgressBar value={task.progress} />
          <div className="profile-task-pct">{task.progress}%</div>
        </div>
      ))}

      <div className="section-title">About</div>
      <p className="about-text">{agent.about}</p>
      <div className="chip-row" style={{ marginTop: 10 }}>
        {agent.skillTags.map((skill) => (
          <span className="chip" key={skill}>
            {skill}
          </span>
        ))}
      </div>
      <p className="about-text" style={{ marginTop: 8, fontSize: 11.5 }}>
        Model: {agent.modelProvider} · {agent.modelName}
      </p>

      <div className="section-title">Activity Feed</div>
      {feed.map((event) => (
        <div className="feed-row" key={event.id}>
          <span className="feed-time">{formatClockTime(event.createdAt)}</span>
          <span>{event.text}</span>
        </div>
      ))}

      <div className="detail-actions">
        {dm && (
          <button
            className="btn accent"
            onClick={() => dispatch({ type: "openPanel", panel: "chat", channelId: dm.id })}
          >
            Message
          </button>
        )}
        {agent.status === "in_focus" && (
          <button
            className="btn"
            onClick={() => dispatch({ type: "openPanel", panel: "focus", agentId: agent.id })}
          >
            View Focus Pod
          </button>
        )}
      </div>
    </PanelShell>
  );
}
