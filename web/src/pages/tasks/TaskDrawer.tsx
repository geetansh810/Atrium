import { useState } from "react";
import { formatTimeAgo, PRIORITY_LABEL } from "../../shared/format";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import { Drawer } from "../../ui/Drawer";
import { Tabs } from "../../ui/Tabs";
import type { TabItem } from "../../ui/Tabs";
import { StatusPill, taskStatusTone } from "../../ui/StatusPill";
import type { PillTone } from "../../ui/StatusPill";
import { Timeline } from "../../ui/Timeline";
import type { TimelineItem } from "../../ui/Timeline";
import { EmptyState } from "../../ui/EmptyState";
import { ProgressBar } from "../../shared/ProgressBar";
import { ConversationThread } from "../../ui/ConversationThread";
import "./TaskDrawer.css";

const EVENT_LABEL: Record<string, string> = {
  created: "Created",
  claimed: "Claimed",
  progress: "Started work",
  completed: "Completed — sent for review",
  approved: "Approved",
  rejected: "Sent back with feedback",
  requeued: "Requeued",
  flagged: "Flagged for a human",
};

function eventTone(eventType: string): PillTone {
  if (eventType === "approved" || eventType === "completed") return "success";
  if (eventType === "rejected" || eventType === "flagged") return "danger";
  if (eventType === "claimed" || eventType === "progress" || eventType === "requeued") return "running";
  return "neutral";
}

interface TaskDrawerProps {
  taskId: string;
  onClose: () => void;
}

// Task detail as a right-edge drawer (replaces WorkspacePanel + ApprovalsPanel
// for the single-task view, MF-3). Deep-links via /tasks/:id — TasksPage
// renders this whenever the route carries an id, so a refresh restores it.
export function TaskDrawer({ taskId, onClose }: TaskDrawerProps) {
  const { state, dispatch } = useApp();
  const nav = useAppNav();
  const [rejecting, setRejecting] = useState(false);
  const [feedback, setFeedback] = useState("");

  const task = state.tasks.find((t) => t.id === taskId);

  if (!task) {
    return (
      <Drawer title="Task" onClose={onClose} width={640}>
        <EmptyState title="Task not found." description="It may have been removed." />
      </Drawer>
    );
  }

  const agent = task.assignedAgentId ? state.agents.find((a) => a.id === task.assignedAgentId) : undefined;
  const channel = task.assignedAgentId
    ? state.channels.find((c) => c.kind === "dm" && c.agentId === task.assignedAgentId)
    : undefined;
  const doneCount = task.subtasks.filter((s) => s.state === "done").length;
  const canReview = task.status === "pending_review" || task.status === "flagged";

  const submitReject = () => {
    if (!feedback.trim()) return;
    dispatch({ type: "rejectTask", taskId: task.id, feedback: feedback.trim() });
    setRejecting(false);
    setFeedback("");
  };

  const timelineItems: TimelineItem[] = [...task.events]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map((e) => ({
      id: e.id,
      label: EVENT_LABEL[e.eventType] ?? e.eventType,
      timestamp: formatTimeAgo(e.createdAt),
      tone: eventTone(e.eventType),
    }));

  const tabs: TabItem[] = [
    {
      key: "overview",
      label: "Overview",
      content: (
        <div className="task-drawer-overview">
          <p className="task-drawer-desc">{task.description}</p>
          <div className="task-drawer-meta-row">
            <StatusPill label={task.status.replace("_", " ")} tone={taskStatusTone(task.status)} />
            <span>Priority: {PRIORITY_LABEL[task.priority] ?? task.priority}</span>
            <span>Skill: {task.requiredSkill}</span>
          </div>
          <ProgressBar value={task.progress} />
          <div className="task-drawer-pct">
            {task.progress}%{task.etaMinutes != null ? ` · ETA: ${task.etaMinutes} minutes` : ""}
          </div>

          {task.flagReason && (
            <>
              <div className="section-title">Flagged — needs a human</div>
              <div className="artifact-box flag">{task.flagReason}</div>
            </>
          )}

          {task.feedback && (
            <>
              <div className="section-title">Review feedback</div>
              <div className="artifact-box feedback">{task.feedback}</div>
            </>
          )}

          {canReview && (
            <div className="task-drawer-review">
              {rejecting ? (
                <>
                  <textarea
                    className="task-drawer-feedback"
                    placeholder={task.status === "flagged" ? "Guidance for the agent…" : "What needs to change?"}
                    value={feedback}
                    onChange={(e) => setFeedback(e.target.value)}
                    autoFocus
                  />
                  <div className="detail-actions">
                    <button className="btn primary sm" disabled={!feedback.trim()} onClick={submitReject}>
                      {task.status === "flagged" ? "Send guidance & resume" : "Reject with feedback"}
                    </button>
                    <button
                      className="btn sm"
                      onClick={() => {
                        setRejecting(false);
                        setFeedback("");
                      }}
                    >
                      Cancel
                    </button>
                  </div>
                </>
              ) : (
                <div className="detail-actions">
                  {task.status === "pending_review" && (
                    <button
                      className="btn accent"
                      onClick={() => dispatch({ type: "approveTask", taskId: task.id })}
                    >
                      Approve
                    </button>
                  )}
                  <button
                    className={`btn${task.status === "flagged" ? " primary" : " danger"}`}
                    onClick={() => setRejecting(true)}
                  >
                    {task.status === "flagged" ? "Reply & unblock" : "Reject"}
                  </button>
                </div>
              )}
            </div>
          )}

          {agent && (
            <div className="detail-actions">
              <button className="btn" onClick={() => nav.openAgent(agent.id)}>
                View {agent.name}
              </button>
            </div>
          )}
        </div>
      ),
    },
    {
      key: "subtasks",
      label: `Subtasks${task.subtasks.length ? ` (${doneCount}/${task.subtasks.length})` : ""}`,
      content:
        task.subtasks.length === 0 ? (
          <EmptyState title="No subtasks." />
        ) : (
          <div>
            {task.subtasks.map((sub) => (
              <div
                key={sub.id}
                className={`subtask-row ${sub.state}`}
                onClick={() => dispatch({ type: "toggleSubtask", taskId: task.id, subtaskId: sub.id })}
              >
                <span className="subtask-check">
                  {sub.state === "done" ? "✓" : sub.state === "doing" ? <span className="subtask-dot" /> : ""}
                </span>
                <span className="subtask-label">{sub.label}</span>
              </div>
            ))}
          </div>
        ),
    },
    {
      key: "output",
      label: "Output",
      content: task.artifact ? (
        <div className="artifact-box">{task.artifact.content}</div>
      ) : (
        <EmptyState title="No output yet." description="The agent's artifact will appear here once produced." />
      ),
    },
    {
      key: "timeline",
      label: "Timeline",
      content: timelineItems.length ? <Timeline items={timelineItems} /> : <EmptyState title="No events yet." />,
    },
    {
      key: "conversation",
      label: "Conversation",
      content: channel ? (
        <ConversationThread channelId={channel.id} placeholder={`Message ${agent?.name ?? "agent"}`} />
      ) : (
        <EmptyState title="No conversation yet." description="Assign this task to an agent to start a DM thread." />
      ),
    },
  ];

  return (
    <Drawer title={task.title} subtitle={agent?.name ?? "Unassigned"} onClose={onClose} width={640}>
      <Tabs tabs={tabs} />
    </Drawer>
  );
}
