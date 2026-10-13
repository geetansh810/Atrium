import type { Agent, Task } from "../shared/types";
import { Avatar } from "../shared/Avatar";
import { useAppNav } from "../shared/nav";
import { StatusPill, taskStatusTone } from "./StatusPill";
import "./EntityLink.css";

// One way to refer to an agent or a task from anywhere. Every cross-entity
// reference (task → agent, agent → manager, task → parent) goes through these
// so no screen is a dead end (UX2).

export function AgentLink({ agent, size = 20 }: { agent: Pick<Agent, "id" | "name">; size?: number }) {
  const nav = useAppNav();
  return (
    <button className="entity-link" onClick={() => nav.openAgent(agent.id)} title={`Open ${agent.name}`}>
      <Avatar name={agent.name} seed={agent.id} size={size} />
      <span className="entity-link-text">{agent.name}</span>
    </button>
  );
}

export function TaskLink({ task, showStatus = true }: { task: Pick<Task, "id" | "title" | "status">; showStatus?: boolean }) {
  const nav = useAppNav();
  return (
    <button className="entity-link" onClick={() => nav.openTask(task.id)} title="Open task">
      <span className="entity-link-text">{task.title}</span>
      {showStatus && <StatusPill label={task.status.replace("_", " ")} tone={taskStatusTone(task.status)} />}
    </button>
  );
}
