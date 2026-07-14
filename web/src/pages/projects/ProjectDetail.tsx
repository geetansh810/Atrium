import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { Avatar } from "../../shared/Avatar";
import { StatusDot } from "../../shared/StatusDot";
import { PRIORITY_LABEL, formatTimeAgo, formatTokens } from "../../shared/format";
import { costPerTask } from "../../shared/mockData";
import { kanbanColumns, synthesizeFeed, TASK_EVENT_LABEL, taskEventTone } from "../../shared/selectors";
import { USE_MOCKS } from "../../shared/config";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { useProjects } from "../../shared/domains/projects";
import type { ProjectStatus, Task } from "../../shared/types";
import { Tabs } from "../../ui/Tabs";
import type { TabItem } from "../../ui/Tabs";
import { StatusPill } from "../../ui/StatusPill";
import type { PillTone } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import { Timeline } from "../../ui/Timeline";
import type { TimelineItem } from "../../ui/Timeline";
import { KanbanBoard } from "../../ui/Kanban";
import type { KanbanColumnData } from "../../ui/Kanban";
import { ProgressBar } from "../../shared/ProgressBar";
import "./ProjectDetail.css";

const STATUS_LABEL: Record<ProjectStatus, string> = {
  planning: "Planning",
  active: "Active",
  on_hold: "On Hold",
  completed: "Completed",
};

function projectStatusTone(status: ProjectStatus): PillTone {
  if (status === "active") return "running";
  if (status === "completed") return "success";
  if (status === "on_hold") return "warning";
  return "neutral";
}

const COLUMN_TITLES: Record<string, string> = {
  queued: "Queued",
  in_progress: "In Progress",
  pending_review: "Review",
  approved: "Done",
};
const COLUMN_ORDER = ["queued", "in_progress", "pending_review", "approved"];
const LEGAL_DRAG: [string, string] = ["pending_review", "approved"];

// Net-new domain, no backend equivalent yet (MF-5) — real tasks are
// client-side-assignable to these mock projects via shared/domains/projects.ts.
export function ProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { state, dispatch } = useApp();
  const nav = useAppNav();
  const { projects, taskLinks, setProjectStatus, setProjectBudgetCap, linkTask, unlinkTask } = useProjects();
  const [addTaskId, setAddTaskId] = useState("");
  const [editingCap, setEditingCap] = useState(false);
  const [capDraft, setCapDraft] = useState("");

  const project = projects.find((p) => p.id === id);

  const linkedIds = project ? taskLinks[project.id] ?? [] : [];
  const linkedTasks = linkedIds
    .map((taskId) => state.tasks.find((t) => t.id === taskId))
    .filter((t): t is Task => Boolean(t));

  // useMemo must run unconditionally (rules of hooks) — the "project not
  // found" early return comes after every hook call.
  const columns: KanbanColumnData<Task>[] = useMemo(() => {
    const byKey = kanbanColumns(linkedTasks);
    return COLUMN_ORDER.map((key) => ({
      key,
      title: COLUMN_TITLES[key] ?? key,
      items: (byKey[key] ?? []).slice().sort((a, b) => a.priority - b.priority),
    }));
  }, [linkedTasks]);

  if (!project) {
    return (
      <div className="project-detail-page">
        <EmptyState title="Project not found." description="It may have been removed." />
      </div>
    );
  }

  const unlinkedTasks = state.tasks.filter((t) => !linkedIds.includes(t.id));
  const owner = project.ownerAgentId ? state.agents.find((a) => a.id === project.ownerAgentId) : undefined;

  const agentIds = new Set(linkedTasks.map((t) => t.assignedAgentId).filter((a): a is string => Boolean(a)));
  if (project.ownerAgentId) agentIds.add(project.ownerAgentId);
  const projectAgents = Array.from(agentIds)
    .map((agentId) => state.agents.find((a) => a.id === agentId))
    .filter((a): a is NonNullable<typeof a> => Boolean(a));

  const costRows = linkedTasks
    .map((task) => ({ task, cost: costPerTask.find((c) => c.taskId === task.id) }))
    .filter((row): row is { task: Task; cost: NonNullable<(typeof costPerTask)[number]> } => Boolean(row.cost));

  const saveCap = () => {
    const parsed = capDraft.trim() === "" ? null : Number(capDraft.replace(/[^0-9]/g, ""));
    setProjectBudgetCap(project.id, parsed && parsed > 0 ? parsed : null);
    setEditingCap(false);
  };

  const handleDrop = (taskId: string, fromColumnKey: string, toColumnKey: string) => {
    if (fromColumnKey === toColumnKey) return;
    if (fromColumnKey === LEGAL_DRAG[0] && toColumnKey === LEGAL_DRAG[1]) {
      dispatch({ type: "approveTask", taskId });
      return;
    }
    dispatch({
      type: "setNotice",
      text: "Agents drive task status — drag a Review card to Done to approve it.",
    });
  };

  const timelineItems: TimelineItem[] = synthesizeFeed(linkedTasks, state.agents, 50).map((item) => ({
    id: item.id,
    label: `${item.agentName ?? "System"} — ${TASK_EVENT_LABEL[item.eventType] ?? item.eventType}: ${item.taskTitle}`,
    timestamp: formatTimeAgo(item.createdAt),
    tone: taskEventTone(item.eventType),
  }));

  const tabs: TabItem[] = [
    {
      key: "overview",
      label: "Overview",
      content: (
        <div className="project-detail-overview">
          <p className="project-detail-desc">{project.description}</p>
          <div className="section-title">Details</div>
          <div className="project-detail-row">
            <span>Status</span>
            <select
              value={project.status}
              onChange={(e) => setProjectStatus(project.id, e.target.value as ProjectStatus)}
            >
              {Object.entries(STATUS_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className="project-detail-row">
            <span>Owner</span>
            <span>
              {owner ? (
                <button className="project-detail-link" onClick={() => nav.openAgent(owner.id)}>
                  {owner.name}
                </button>
              ) : (
                "—"
              )}
            </span>
          </div>
          <div className="project-detail-row">
            <span>Created</span>
            <span>{formatTimeAgo(project.createdAt)}</span>
          </div>
          <div className="project-detail-row">
            <span>Linked tasks</span>
            <span>{linkedTasks.length}</span>
          </div>
        </div>
      ),
    },
    {
      key: "tasks",
      label: `Tasks${linkedTasks.length ? ` (${linkedTasks.length})` : ""}`,
      content: (
        <div className="project-detail-tasks">
          <div className="project-detail-add-task">
            <select value={addTaskId} onChange={(e) => setAddTaskId(e.target.value)}>
              <option value="">Add an existing task…</option>
              {unlinkedTasks.map((task) => (
                <option key={task.id} value={task.id}>
                  {task.title}
                </option>
              ))}
            </select>
            <button
              className="btn sm"
              disabled={!addTaskId}
              onClick={() => {
                linkTask(project.id, addTaskId);
                setAddTaskId("");
              }}
            >
              Add
            </button>
          </div>
          {linkedTasks.length === 0 ? (
            <EmptyState title="No tasks linked yet." description="Add a real task above to track it under this project." />
          ) : (
            <div className="project-detail-board">
              <KanbanBoard
                columns={columns}
                getId={(task) => task.id}
                onDrop={handleDrop}
                renderCard={(task) => {
                  const agent = task.assignedAgentId ? state.agents.find((a) => a.id === task.assignedAgentId) : undefined;
                  return (
                    <div className="project-task-card">
                      <div className="project-task-card-top">
                        <button className="project-task-card-title" onClick={() => nav.openTask(task.id)}>
                          {task.title}
                        </button>
                        <button
                          className="project-task-card-remove"
                          title="Remove from project"
                          onClick={(e) => {
                            e.stopPropagation();
                            unlinkTask(project.id, task.id);
                          }}
                        >
                          ×
                        </button>
                      </div>
                      <ProgressBar value={task.progress} height={4} />
                      <div className="project-task-card-meta">
                        {agent ? (
                          <span>
                            <Avatar name={agent.name} seed={agent.id} size={16} /> {agent.name}
                          </span>
                        ) : (
                          <span>Unassigned</span>
                        )}
                        <span>{PRIORITY_LABEL[task.priority] ?? task.priority}</span>
                      </div>
                    </div>
                  );
                }}
              />
            </div>
          )}
        </div>
      ),
    },
    {
      key: "artifacts",
      label: "Artifacts",
      content: (() => {
        const withArtifacts = linkedTasks.filter((t) => t.artifact);
        return withArtifacts.length === 0 ? (
          <EmptyState title="No artifacts yet." description="Output from this project's tasks will appear here." />
        ) : (
          <div className="project-detail-artifacts">
            {withArtifacts.map((task) => (
              <div key={task.id}>
                <div className="section-title">{task.title}</div>
                <div className="artifact-box">{task.artifact?.content}</div>
              </div>
            ))}
          </div>
        );
      })(),
    },
    {
      key: "agents",
      label: `Agents${projectAgents.length ? ` (${projectAgents.length})` : ""}`,
      content:
        projectAgents.length === 0 ? (
          <EmptyState title="No agents involved yet." />
        ) : (
          <div className="project-detail-agents">
            {projectAgents.map((agent) => (
              <button key={agent.id} className="project-detail-agent-row" onClick={() => nav.openAgent(agent.id)}>
                <Avatar name={agent.name} seed={agent.id} size={26} />
                <span className="project-detail-agent-name">{agent.name}</span>
                <span className="project-detail-agent-role">{agent.roleTitle}</span>
                <StatusDot status={agent.status} />
              </button>
            ))}
          </div>
        ),
    },
    {
      key: "timeline",
      label: "Timeline",
      content: timelineItems.length ? <Timeline items={timelineItems} /> : <EmptyState title="No events yet." />,
    },
    {
      key: "budget",
      label: "Budget",
      content: (
        <div className="project-detail-budget">
          <div className="section-title">Budget cap</div>
          <div className="project-detail-row">
            <span>Cap</span>
            {editingCap ? (
              <input
                className="project-detail-cap-input"
                value={capDraft}
                autoFocus
                onChange={(e) => setCapDraft(e.target.value)}
                onBlur={saveCap}
                onKeyDown={(e) => e.key === "Enter" && saveCap()}
              />
            ) : (
              <button
                className="project-detail-cap-btn"
                onClick={() => {
                  setEditingCap(true);
                  setCapDraft(project.budgetCapTokens != null ? String(project.budgetCapTokens) : "");
                }}
              >
                {project.budgetCapTokens != null ? formatTokens(project.budgetCapTokens) : "Not set — click to add"}
              </button>
            )}
          </div>

          <div className="section-title">Cost per task {!USE_MOCKS && <StatusPill label="Sample data" tone="warning" />}</div>
          {costRows.length === 0 ? (
            <EmptyState title="No cost data for this project's tasks yet." />
          ) : (
            costRows.map(({ task, cost }) => (
              <div className="cost-row" key={task.id}>
                <span>{task.title}</span>
                <span className="mono">
                  {formatTokens(cost.tokens)} tok · ${cost.costUsd.toFixed(2)}
                </span>
              </div>
            ))
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="project-detail-page">
      <button className="project-detail-back" onClick={() => navigate("/projects")}>
        ← Projects
      </button>
      <header className="project-detail-head">
        <div className="project-detail-head-info">
          <span className="project-detail-name">{project.name}</span>
          <span className="project-detail-meta">
            <StatusPill label={STATUS_LABEL[project.status]} tone={projectStatusTone(project.status)} />
          </span>
        </div>
      </header>

      <Tabs tabs={tabs} />
    </div>
  );
}
