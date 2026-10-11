import type { Task } from "../../shared/types";
import { useAuthSession } from "../../shared/auth";
import { USE_MOCKS } from "../../shared/config";
import { useCostPerTask } from "../../shared/queries";
import { formatCost, formatTimeAgo, formatTokens } from "../../shared/format";
import { useApp } from "../../shared/store";
import { useActorName } from "../../shared/actorName";
import { AgentLink } from "../../ui/EntityLink";
import "./TaskAccountability.css";

function periodOf(iso: string | undefined): string {
  const d = iso ? new Date(iso) : new Date();
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

// "Who did this, with what model, what did it cost, who approved it" — the
// UX3 Done-when, answered in one block from the task's own audit events.
export function TaskAccountability({ task }: { task: Task }) {
  const { state } = useApp();
  const actorName = useActorName();
  const companyId = useAuthSession()?.companyId ?? "";

  const events = [...task.events].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const completed = [...events].reverse().find((e) => e.eventType === "completed");
  const approved = [...events].reverse().find((e) => e.eventType === "approved");
  const rejections = events.filter((e) => e.eventType === "rejected");
  const claimed = [...events].reverse().find((e) => e.eventType === "claimed");

  const agent = task.assignedAgentId
    ? state.agents.find((a) => a.id === task.assignedAgentId)
    : completed?.actor.startsWith("agent:")
      ? state.agents.find((a) => a.id === completed.actor.slice(6))
      : undefined;

  const payload = completed?.payload ?? {};
  const model =
    typeof payload.model === "string" ? payload.model : agent ? `${agent.modelProvider}/${agent.modelName}` : null;
  const promptVersion = typeof payload.promptVersion === "number" ? payload.promptVersion : null;

  const cost = useCostPerTask(USE_MOCKS ? "" : companyId, periodOf(completed?.createdAt ?? claimed?.createdAt));
  const taskCost = cost.data?.find((c) => c.taskId === task.id);
  const costText = USE_MOCKS
    ? "Not tracked in mock mode"
    : cost.isLoading
      ? "Loading…"
      : taskCost
        ? `${formatCost(taskCost.costUsd)} · ${formatTokens(taskCost.tokens)} tokens`
        : "No spend recorded yet";

  const reviewText = approved
    ? `Approved by ${actorName(approved.actor)} ${formatTimeAgo(approved.createdAt)}`
    : task.status === "pending_review"
      ? "Waiting for a reviewer"
      : task.status === "flagged"
        ? "Flagged. Needs a person to reply"
        : "Not reviewed yet";

  return (
    <dl className="task-accountability">
      <div>
        <dt>Worked by</dt>
        <dd>{agent ? <AgentLink agent={agent} /> : "Not claimed yet"}</dd>
      </div>
      <div>
        <dt>Model</dt>
        <dd className="task-accountability-mono">
          {model ?? "—"}
          {promptVersion != null && <span className="task-accountability-faint"> · prompt v{promptVersion}</span>}
        </dd>
      </div>
      <div>
        <dt>Cost</dt>
        <dd>{costText}</dd>
      </div>
      <div>
        <dt>Review</dt>
        <dd>{reviewText}</dd>
      </div>
      {rejections.length > 0 && (
        <div>
          <dt>Sent back</dt>
          <dd>
            {rejections.length}× · last by {actorName(rejections[rejections.length - 1].actor)}
          </dd>
        </div>
      )}
      {events.filter((e) => e.eventType === "claimed").length > 1 && (
        <div>
          <dt>Attempts</dt>
          <dd>{events.filter((e) => e.eventType === "claimed").length}</dd>
        </div>
      )}
    </dl>
  );
}
