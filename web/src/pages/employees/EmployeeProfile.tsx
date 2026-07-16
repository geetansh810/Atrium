import { useNavigate, useParams } from "react-router";
import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { StatusDot, STATUS_LABEL } from "../../shared/StatusDot";
import { formatFocusTime, formatTimeAgo } from "../../shared/format";
import { USE_MOCKS, DEV_COMPANY_ID } from "../../shared/config";
import { synthesizeFeed, TASK_EVENT_LABEL } from "../../shared/selectors";
import { useAgentMemories } from "../../shared/queries";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { Tabs } from "../../ui/Tabs";
import type { TabItem } from "../../ui/Tabs";
import { StatCard } from "../../ui/StatCard";
import { StatusPill, taskStatusTone } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import { Feed } from "../../ui/Feed";
import type { FeedEntry } from "../../ui/Feed";
import { ConversationThread } from "../../ui/ConversationThread";
import "./EmployeeProfile.css";

const ACTIVE_STATUSES = new Set(["queued", "claimed", "in_progress", "flagged"]);

// Memory browsing (16 §3) is real-API-only — no mock fixture for it ever
// existed, same "Live API" precedent SettingsPage's Model Catalog card set.
function MockMemoryTab(_props: { agentId: string }) {
  return (
    <EmptyState
      title="Memory browsing is live-API-only."
      description="No mock fixture exists for it — switch off VITE_USE_MOCKS to see real learned memories."
    />
  );
}

function ApiMemoryTab({ agentId }: { agentId: string }) {
  const memories = useAgentMemories(DEV_COMPANY_ID, agentId).data ?? [];
  if (memories.length === 0) {
    return <EmptyState title="No memories yet." description="This agent hasn't learned anything scoped to it yet." />;
  }
  return (
    <div className="employee-profile-memories">
      {memories.map((m) => (
        <div key={m.id} className="employee-profile-memory-row">
          <div className="employee-profile-memory-head">
            <span className="chip">{m.kind}</span>
            <StatusPill
              label={m.status.replace("_", " ")}
              tone={m.status === "active" ? "success" : m.status === "pending_review" ? "warning" : "neutral"}
            />
          </div>
          <p className="employee-profile-memory-content">{m.content}</p>
          <span className="employee-profile-meta">
            Used {m.useCount}× · {m.lastUsedAt ? `last used ${formatTimeAgo(m.lastUsedAt)}` : "never recalled"}
          </span>
        </div>
      ))}
    </div>
  );
}

const MemoryTab = USE_MOCKS ? MockMemoryTab : ApiMemoryTab;

export function EmployeeProfile() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { state, dispatch } = useApp();
  const nav = useAppNav();

  const agent = state.agents.find((a) => a.id === id);
  if (!agent) {
    return (
      <div className="employee-profile-page">
        <EmptyState title="Agent not found." description="It may have been removed." />
      </div>
    );
  }

  const stats = state.agentStats[agent.id] ?? { tasksCompleted: 0, successRate: 100, focusMinutes: 0 };
  const manager = state.agents.find((a) => a.id === agent.managerAgentId);
  const reports = state.agents.filter((a) => a.managerAgentId === agent.id);
  const dm = state.channels.find((c) => c.kind === "dm" && c.agentId === agent.id);
  const agentTasks = state.tasks
    .filter((t) => t.assignedAgentId === agent.id)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const workload = agentTasks.filter((t) => ACTIVE_STATUSES.has(t.status)).length;
  const activityFeed: FeedEntry[] = synthesizeFeed(agentTasks, [agent], 30).map((f) => ({
    id: f.id,
    agentName: null,
    text: `${TASK_EVENT_LABEL[f.eventType] ?? f.eventType} — ${f.taskTitle}`,
    createdAt: f.createdAt,
  }));

  const tabs: TabItem[] = [
    {
      key: "overview",
      label: "Overview",
      content: (
        <div className="employee-profile-overview">
          <p className="employee-profile-about">{agent.about}</p>
          <div className="chip-row">
            {agent.skillTags.map((skill) => (
              <span className="chip" key={skill}>
                {skill}
              </span>
            ))}
          </div>
          <div className="section-title">Details</div>
          <div className="employee-profile-detail-row">
            <span>Model</span>
            <span className="mono">{agent.modelProvider} · {agent.modelName}</span>
          </div>
          <div className="employee-profile-detail-row">
            <span>Manager</span>
            <span>{manager ? manager.name : "—"}</span>
          </div>
          {reports.length > 0 && (
            <div className="employee-profile-detail-row">
              <span>Direct reports</span>
              <span>{reports.map((r) => r.name).join(", ")}</span>
            </div>
          )}
          <div className="employee-profile-detail-row">
            <span>Active workload</span>
            <span>{workload} task{workload === 1 ? "" : "s"}</span>
          </div>
        </div>
      ),
    },
    {
      key: "tasks",
      label: `Tasks${agentTasks.length ? ` (${agentTasks.length})` : ""}`,
      content:
        agentTasks.length === 0 ? (
          <EmptyState title="No tasks assigned yet." />
        ) : (
          <div className="employee-profile-tasks">
            {agentTasks.map((task) => (
              <button key={task.id} className="employee-profile-task-row" onClick={() => nav.openTask(task.id)}>
                <span className="employee-profile-task-title">{task.title}</span>
                <span style={{ width: 80, flexShrink: 0 }}>
                  <ProgressBar value={task.progress} height={4} />
                </span>
                <StatusPill label={task.status.replace("_", " ")} tone={taskStatusTone(task.status)} />
              </button>
            ))}
          </div>
        ),
    },
    {
      key: "activity",
      label: "Activity",
      content: <Feed items={activityFeed} emptyLabel="No activity yet." />,
    },
    {
      key: "conversation",
      label: "Conversation",
      content: dm ? (
        <ConversationThread channelId={dm.id} placeholder={`Message ${agent.name}`} />
      ) : (
        <EmptyState title="No conversation channel yet." />
      ),
    },
    {
      key: "memory",
      label: "Memory",
      content: <MemoryTab agentId={agent.id} />,
    },
    {
      key: "performance",
      label: "Performance",
      content: (
        <div className="employee-profile-stats">
          <StatCard label="Tasks Completed" value={stats.tasksCompleted} />
          <StatCard label="Success Rate" value={`${stats.successRate}%`} />
          <StatCard label="Focus Time" value={formatFocusTime(stats.focusMinutes)} />
          {!USE_MOCKS && (
            <p className="employee-profile-note">
              Focus Time isn't tracked yet — no presence source exists until a future milestone.
            </p>
          )}
        </div>
      ),
    },
    {
      key: "settings",
      label: "Settings",
      content: (
        <div className="employee-profile-settings">
          <div className="section-title">Status</div>
          <div className="employee-profile-detail-row">
            <span>Current</span>
            <span><StatusDot status={agent.status} /> {STATUS_LABEL[agent.status]}</span>
          </div>
          <div className="detail-actions">
            <button
              className={`btn${agent.paused ? " accent" : " danger"}`}
              onClick={() => dispatch({ type: "setAgentPaused", agentId: agent.id, paused: !agent.paused })}
            >
              {agent.paused ? "Resume Agent" : "Pause Agent"}
            </button>
            {agent.status === "in_focus" && (
              <button className="btn" onClick={() => dispatch({ type: "exitFocusPod", agentId: agent.id })}>
                Exit Focus Pod
              </button>
            )}
          </div>
          {agent.paused && (
            <p className="employee-profile-note">
              Paused agents are skipped at claim time — nothing new gets assigned until resumed.
            </p>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="employee-profile-page">
      <button className="employee-profile-back" onClick={() => navigate("/employees")}>
        ← Employees
      </button>
      <header className="employee-profile-head">
        <Avatar name={agent.name} seed={agent.id} size={56} square />
        <div className="employee-profile-head-info">
          <span className="employee-profile-name">{agent.name}</span>
          <span className="employee-profile-meta">
            <StatusDot status={agent.status} /> {STATUS_LABEL[agent.status]}
          </span>
          <span className="employee-profile-meta">{agent.roleTitle}</span>
          <span className="employee-profile-meta">Joined {formatTimeAgo(agent.joinedAt)}</span>
        </div>
        {dm && (
          <button className="btn accent" onClick={() => nav.openChat(dm.id)}>
            Message
          </button>
        )}
      </header>

      <Tabs tabs={tabs} />
    </div>
  );
}
