import { confirmAction } from "../../shared/confirm";
import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { Avatar } from "../../shared/Avatar";
import { formatTimeAgo, PRIORITY_LABEL } from "../../shared/format";
import { skillColor } from "../../shared/skillColor";
import { describeStalledSummary, findStalledTasks, kanbanColumns, summarizeStalledTasks } from "../../shared/selectors";
import { useApp } from "../../shared/store";
import { ProgressBar } from "../../shared/ProgressBar";
import { KanbanBoard } from "../../ui/Kanban";
import type { KanbanColumnData } from "../../ui/Kanban";
import { Banner } from "../../ui/Banner";
import { StatusPill, taskStatusTone } from "../../ui/StatusPill";
import type { Task } from "../../shared/types";
import { TaskDrawer } from "./TaskDrawer";
import "./TasksPage.css";

const COLUMN_TITLES: Record<string, string> = {
  queued: "Queued",
  in_progress: "In Progress",
  pending_review: "Review",
  approved: "Done",
};
const COLUMN_ORDER = ["queued", "in_progress", "pending_review", "approved"];

// Review→Done is the one drag the backend actually honors (it's an approve).
// Everything else is a no-op the human gets a toast for — agents drive
// status, not drag-and-drop (identical rule in mock and API mode).
const LEGAL_DRAG: [string, string] = ["pending_review", "approved"];

export function TasksPage() {
  const { state, dispatch } = useApp();
  const navigate = useNavigate();
  const { id: openTaskId } = useParams();
  const [needsReworkOnly, setNeedsReworkOnly] = useState(false);
  const [view, setView] = useState<"list" | "board">("list");

  const visibleTasks = needsReworkOnly ? state.tasks.filter((t) => t.feedback) : state.tasks;
  const agentById = new Map(state.agents.map((a) => [a.id, a]));
  const reworkCount = state.tasks.filter((t) => t.feedback).length;

  const stalledSummary = summarizeStalledTasks(findStalledTasks(state.tasks, state.agents));

  const columns: KanbanColumnData<Task>[] = useMemo(() => {
    const byKey = kanbanColumns(visibleTasks);
    return COLUMN_ORDER.map((key) => ({
      key,
      title: COLUMN_TITLES[key] ?? key,
      items: (byKey[key] ?? []).slice().sort((a, b) => a.priority - b.priority),
    }));
  }, [visibleTasks]);

  const handleDrop = (taskId: string, fromColumnKey: string, toColumnKey: string) => {
    if (fromColumnKey === toColumnKey) return;
    if (fromColumnKey === LEGAL_DRAG[0] && toColumnKey === LEGAL_DRAG[1]) {
      const task = state.tasks.find((t) => t.id === taskId);
      void confirmAction({
        title: `Approve "${task?.title ?? "this task"}"?`,
        body: "Approving marks the work as shipped.",
        confirmLabel: "Approve",
      }).then((ok) => {
        if (ok) dispatch({ type: "approveTask", taskId });
      });
      return;
    }
    dispatch({
      type: "setNotice",
      text: "Agents drive task status — drag a Review card to Done to approve it. Everything else happens automatically.",
    });
  };

  return (
    <div className="tasks-page">
      <header className="tasks-page-head">
        <div>
          <h1>Tasks</h1>
          <p>
            {view === "board"
              ? "Drag a card from Review to Done to approve it. Agents move everything else."
              : "Grouped by status. Open a row to review it."}
          </p>
        </div>
        <div className="tasks-page-tools">
          <div className="tasks-page-views" role="group" aria-label="Task view">
            <button className={`btn sm${view === "list" ? " primary" : ""}`} type="button" onClick={() => setView("list")}>
              List
            </button>
            <button className={`btn sm${view === "board" ? " primary" : ""}`} type="button" onClick={() => setView("board")}>
              Board
            </button>
          </div>
          <button
            className={`btn sm${needsReworkOnly ? " primary" : ""}`}
            onClick={() => setNeedsReworkOnly((v) => !v)}
            disabled={reworkCount === 0}
          >
            Needs rework ({reworkCount})
          </button>
        </div>
      </header>

      {stalledSummary.length > 0 && (
        <Banner
          tone={stalledSummary.some((s) => s.reason === "no-agent-with-skill") ? "danger" : "warning"}
          title="Some tasks aren't moving"
          description={stalledSummary.map(describeStalledSummary).join("\n")}
        />
      )}

      {view === "list" ? (
        <div className="tasks-list">
          {columns.map((column) => (
            <section key={column.key}>
              <h2>
                {column.title}
                <span>{column.items.length}</span>
              </h2>
              {column.items.length === 0 ? (
                <p className="tasks-list-empty">Nothing here.</p>
              ) : (
                <ul>
                  {column.items.map((task) => {
                    const agent = task.assignedAgentId ? agentById.get(task.assignedAgentId) : undefined;
                    return (
                      <li key={task.id}>
                        <button type="button" onClick={() => navigate(`/tasks/${task.id}`)}>
                          <StatusPill label={task.status.replaceAll("_", " ")} tone={taskStatusTone(task.status)} />
                          <span className="tasks-list-title">{task.title}</span>
                          <span className="tasks-list-who">{agent?.name ?? "Unassigned"}</span>
                          <span className="tasks-list-when">{formatTimeAgo(task.createdAt)}</span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>
          ))}
        </div>
      ) : (
      <div className="tasks-page-board">
        <KanbanBoard
          columns={columns}
          getId={(task) => task.id}
          onDrop={handleDrop}
          renderCard={(task) => {
            const agent = task.assignedAgentId ? agentById.get(task.assignedAgentId) : undefined;
            return (
              <div className="task-card" onClick={() => navigate(`/tasks/${task.id}`)}>
                <div className="task-card-top">
                  <span className="task-card-skill" style={{ background: skillColor(task.requiredSkill) }}>
                    {task.requiredSkill.slice(0, 1)}
                  </span>
                  <span className="task-card-title">{task.title}</span>
                </div>
                {task.feedback && <div className="task-card-rework">Needs rework</div>}
                <ProgressBar value={task.progress} height={4} />
                <div className="task-card-meta">
                  {agent ? (
                    <span className="task-card-agent">
                      <Avatar name={agent.name} seed={agent.id} size={18} />
                      {agent.name}
                    </span>
                  ) : (
                    <span className="task-card-agent">Unassigned</span>
                  )}
                  <span className="task-card-priority">{PRIORITY_LABEL[task.priority] ?? task.priority}</span>
                </div>
              </div>
            );
          }}
        />
      </div>
      )}

      {openTaskId && <TaskDrawer taskId={openTaskId} onClose={() => navigate("/tasks")} />}
    </div>
  );
}
