import { useState } from "react";
import { Avatar } from "../../shared/Avatar";
import { formatTimeAgo } from "../../shared/format";
import { useApp } from "../../shared/store";
import type { Task } from "../../shared/types";
import { PanelShell } from "./PanelShell";

// The two-clicks-from-login escalation surface (01-product-spec §3.9):
// every pending_review + flagged item, newest first.
export function ApprovalsPanel() {
  const { state, dispatch } = useApp();
  const [feedbackFor, setFeedbackFor] = useState<string | null>(null);
  const [feedback, setFeedback] = useState("");

  const queue = state.tasks
    .filter((t) => t.status === "pending_review" || t.status === "flagged")
    .sort((a, b) => (b.completedAt ?? b.createdAt).localeCompare(a.completedAt ?? a.createdAt));

  const agentById = new Map(state.agents.map((a) => [a.id, a]));

  const sendBack = (task: Task) => {
    if (!feedback.trim()) return;
    dispatch({ type: "rejectTask", taskId: task.id, feedback: feedback.trim() });
    setFeedbackFor(null);
    setFeedback("");
  };

  return (
    <PanelShell
      title="Approvals & Escalations"
      subtitle={`${queue.length} item${queue.length === 1 ? "" : "s"} waiting on you`}
      width={520}
    >
      {queue.length === 0 && <div className="empty-note">All clear — nothing needs review. 🎉</div>}

      {queue.map((task) => {
        const agent = task.assignedAgentId ? agentById.get(task.assignedAgentId) : undefined;
        const isFlagged = task.status === "flagged";
        return (
          <div className="approval-card" key={task.id}>
            <div className="approval-top">
              <span className="approval-title">{task.title}</span>
              <span className={`status-badge ${task.status}`}>
                {isFlagged ? "Flagged" : "Pending Review"}
              </span>
            </div>
            {agent && (
              <div className="approval-agent">
                <Avatar name={agent.name} seed={agent.id} size={20} />
                {agent.name} · {formatTimeAgo(task.completedAt ?? task.createdAt)}
              </div>
            )}

            {isFlagged && task.flagReason && <div className="artifact-box flag">{task.flagReason}</div>}
            {!isFlagged && task.artifact && (
              <div className="artifact-box">{task.artifact.content}</div>
            )}

            {feedbackFor === task.id ? (
              <>
                <textarea
                  className="approval-feedback"
                  placeholder={isFlagged ? "Guidance for the agent…" : "What needs to change?"}
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  autoFocus
                />
                <div className="approval-actions">
                  <button className="btn primary sm" disabled={!feedback.trim()} onClick={() => sendBack(task)}>
                    {isFlagged ? "Send guidance & resume" : "Reject with feedback"}
                  </button>
                  <button
                    className="btn sm"
                    onClick={() => {
                      setFeedbackFor(null);
                      setFeedback("");
                    }}
                  >
                    Cancel
                  </button>
                </div>
              </>
            ) : (
              <div className="approval-actions">
                {!isFlagged && (
                  <button className="btn accent sm" onClick={() => dispatch({ type: "approveTask", taskId: task.id })}>
                    Approve
                  </button>
                )}
                <button
                  className={`btn sm${isFlagged ? " primary" : " danger"}`}
                  onClick={() => {
                    setFeedbackFor(task.id);
                    setFeedback("");
                  }}
                >
                  {isFlagged ? "Reply & unblock" : "Reject"}
                </button>
                <button
                  className="btn sm"
                  onClick={() => dispatch({ type: "openPanel", panel: "workspace", taskId: task.id })}
                >
                  Open task
                </button>
              </div>
            )}
          </div>
        );
      })}
    </PanelShell>
  );
}
