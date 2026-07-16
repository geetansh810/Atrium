import { useState } from "react";
import { useNavigate } from "react-router";
import { Avatar } from "../../shared/Avatar";
import { formatTimeAgo } from "../../shared/format";
import { USE_MOCKS } from "../../shared/config";
import { useAuthSession } from "../../shared/auth";
import { useMemoryReviewQueue, useReviewMemoryMutation } from "../../shared/queries";
import { useApp } from "../../shared/store";
import type { Task } from "../../shared/types";
import { StatusPill } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import { Tabs } from "../../ui/Tabs";
import type { TabItem } from "../../ui/Tabs";
import "./ReviewInbox.css";

// Memory review-queue (16 §3, M-LN1/M-LN2) is real-API-only — no mock
// fixture for pending memories has ever existed, same "Live API" precedent
// EmployeeProfile's Memory tab set (M2.6).
function MockMemoriesTab() {
  return (
    <EmptyState
      title="Memory review is live-API-only."
      description="No mock fixture exists for pending memories — switch off VITE_USE_MOCKS to review real learned memories."
    />
  );
}

function ApiMemoriesTab() {
  const companyId = useAuthSession()?.companyId ?? "";
  const { data: queue = [] } = useMemoryReviewQueue(companyId);
  const { state } = useApp();
  const review = useReviewMemoryMutation(companyId);
  const agentById = new Map(state.agents.map((a) => [a.id, a]));

  if (queue.length === 0) {
    return <EmptyState title="No memories waiting on review." />;
  }

  return (
    <div className="review-inbox-memories">
      {queue.map((m) => {
        const agent = m.agentId ? agentById.get(m.agentId) : undefined;
        const scopeLabel = m.scope === "agent" && agent ? agent.name : m.roleKey ?? m.scope;
        return (
          <div className="review-card" key={m.id}>
            <div className="review-card-top">
              <span className="review-card-title">{scopeLabel}</span>
              <span className="chip">{m.kind}</span>
              <StatusPill label={`scope: ${m.scope}`} tone="neutral" />
            </div>
            <p className="review-inbox-memory-content">{m.content}</p>
            <div className="review-card-actions">
              <button
                className="btn accent sm"
                disabled={review.isPending}
                onClick={() => review.mutate({ memoryId: m.id, action: "approve" })}
              >
                Approve
              </button>
              <button
                className="btn danger sm"
                disabled={review.isPending}
                onClick={() => review.mutate({ memoryId: m.id, action: "reject" })}
              >
                Reject
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}

const MemoriesTab = USE_MOCKS ? MockMemoriesTab : ApiMemoriesTab;

// The two-clicks-from-login escalation surface (01-product-spec §3.9),
// promoted to a routed page (MF-3) — replaces ApprovalsPanel. Every
// pending_review + flagged item, newest first; count matches escalationCount
// so the nav badge and this page never disagree. Gained a Memories tab
// (M-LN2) for the reject→lesson→review→approve learning loop.
export function ReviewInbox() {
  const { state, dispatch } = useApp();
  const navigate = useNavigate();
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

  const tasksTab = (
    <>
      {queue.length === 0 && (
        <EmptyState title="All clear — nothing needs review." />
      )}

      {queue.map((task) => {
        const agent = task.assignedAgentId ? agentById.get(task.assignedAgentId) : undefined;
        const isFlagged = task.status === "flagged";
        return (
          <div className="review-card" key={task.id}>
            <div className="review-card-top">
              <span className="review-card-title">{task.title}</span>
              <StatusPill label={isFlagged ? "Flagged" : "Pending Review"} tone={isFlagged ? "danger" : "warning"} />
            </div>
            {agent && (
              <div className="review-card-agent">
                <Avatar name={agent.name} seed={agent.id} size={20} />
                {agent.name} · {formatTimeAgo(task.completedAt ?? task.createdAt)}
              </div>
            )}

            {isFlagged && task.flagReason && <div className="artifact-box flag">{task.flagReason}</div>}
            {!isFlagged && task.artifact && <div className="artifact-box">{task.artifact.content}</div>}

            {feedbackFor === task.id ? (
              <>
                <textarea
                  className="review-feedback"
                  placeholder={isFlagged ? "Guidance for the agent…" : "What needs to change?"}
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  autoFocus
                />
                <div className="review-card-actions">
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
              <div className="review-card-actions">
                {!isFlagged && (
                  <button
                    className="btn accent sm"
                    onClick={() => dispatch({ type: "approveTask", taskId: task.id })}
                  >
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
                <button className="btn sm" onClick={() => navigate(`/tasks/${task.id}`)}>
                  Open task
                </button>
              </div>
            )}
          </div>
        );
      })}
    </>
  );

  const tabs: TabItem[] = [
    { key: "tasks", label: `Tasks (${queue.length})`, content: tasksTab },
    { key: "memories", label: "Memories", content: <MemoriesTab /> },
  ];

  return (
    <div className="review-inbox">
      <header className="review-inbox-head">
        <h1>Review Inbox</h1>
        <p>{queue.length} item{queue.length === 1 ? "" : "s"} waiting on you.</p>
      </header>

      <Tabs tabs={tabs} />
    </div>
  );
}
