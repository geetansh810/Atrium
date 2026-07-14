// Pure derivations over AppState — the one place both mockStore and apiStore
// agree on how to turn the same shapes into page-ready widgets. No dispatches,
// no side effects, no dependency on storeTypes.ts (keeps this safe to import
// from any page without risking a provider coupling).
import type { Agent, Budget, Task, TaskEvent } from "./types";

export interface Kpis {
  activeAgents: number;
  totalAgents: number;
  tasksInProgress: number;
  tasksCompletedToday: number;
  awaitingReview: number;
}

export function computeKpis(agents: Agent[], tasks: Task[]): Kpis {
  const today = new Date().toDateString();
  return {
    activeAgents: agents.filter((a) => a.status !== "offline").length,
    totalAgents: agents.length,
    tasksInProgress: tasks.filter((t) => t.status === "claimed" || t.status === "in_progress").length,
    tasksCompletedToday: tasks.filter(
      (t) => t.completedAt && new Date(t.completedAt).toDateString() === today,
    ).length,
    awaitingReview: tasks.filter((t) => t.status === "pending_review" || t.status === "flagged").length,
  };
}

export interface BudgetBurn {
  spentTokens: number;
  capTokens: number;
  pct: number;
  warn: boolean; // >= 80%, mirrors accountability's default alert_pct
}

// Company-wide cap = the budget row with agentId === null (07 §accountability).
export function companyBudgetBurn(budgets: Budget[]): BudgetBurn | null {
  const company = budgets.find((b) => b.agentId === null);
  if (!company || company.capTokens <= 0) return null;
  const pct = Math.min(100, Math.round((company.spentTokens / company.capTokens) * 100));
  return { spentTokens: company.spentTokens, capTokens: company.capTokens, pct, warn: pct >= 80 };
}

// One-line status summary for the Mission Control header.
export function companyPulse(agents: Agent[], tasks: Task[], budgets: Budget[]): string {
  const kpis = computeKpis(agents, tasks);
  const burn = companyBudgetBurn(budgets);
  const parts = [
    `${kpis.activeAgents}/${kpis.totalAgents} agents active`,
    `${kpis.tasksInProgress} task${kpis.tasksInProgress === 1 ? "" : "s"} running`,
    `${kpis.awaitingReview} awaiting review`,
  ];
  if (burn) parts.push(`budget ${burn.pct}% burned`);
  return parts.join(" · ");
}

// The single highest-priority task currently being worked, for the Live
// Mission hero. Lower priority number = higher priority (1 = Critical).
export function liveMissionTask(tasks: Task[]): Task | null {
  const running = tasks.filter((t) => t.status === "claimed" || t.status === "in_progress");
  if (running.length === 0) return null;
  return [...running].sort((a, b) => a.priority - b.priority)[0];
}

export interface FeedItem {
  id: string;
  taskId: string;
  taskTitle: string;
  agentName: string | null;
  eventType: string;
  createdAt: string;
}

// Flattens every task's events into one reverse-chronological feed. `agents`
// resolves the `agent:<id>` actor string to a display name.
export function synthesizeFeed(tasks: Task[], agents: Agent[], limit = 20): FeedItem[] {
  const agentById = new Map(agents.map((a) => [a.id, a]));
  const items: FeedItem[] = tasks.flatMap((task) =>
    task.events.map((e: TaskEvent) => {
      const agentId = e.actor.startsWith("agent:") ? e.actor.slice("agent:".length) : null;
      return {
        id: e.id,
        taskId: task.id,
        taskTitle: task.title,
        agentName: agentId ? (agentById.get(agentId)?.name ?? null) : null,
        eventType: e.eventType,
        createdAt: e.createdAt,
      };
    }),
  );
  return items.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, limit);
}

// MF-3: kanban board columns keyed by task status.
export function kanbanColumns(tasks: Task[]): Record<string, Task[]> {
  const columns: Record<string, Task[]> = {
    queued: [],
    in_progress: [],
    pending_review: [],
    approved: [],
  };
  for (const task of tasks) {
    const key = task.status === "claimed" ? "in_progress" : task.status === "flagged" ? "pending_review" : task.status;
    if (!columns[key]) columns[key] = [];
    columns[key].push(task);
  }
  return columns;
}

export type TaskEventTone = "neutral" | "info" | "running" | "success" | "warning" | "danger";

// Human labels + tones for Task.events entries — shared by the task detail
// timeline (TaskDrawer), the project timeline (ProjectDetail, MF-5), and
// anywhere else task events render as a list.
export const TASK_EVENT_LABEL: Record<string, string> = {
  created: "Created",
  claimed: "Claimed",
  progress: "Started work",
  completed: "Completed — sent for review",
  approved: "Approved",
  rejected: "Sent back with feedback",
  requeued: "Requeued",
  flagged: "Flagged for a human",
};

export function taskEventTone(eventType: string): TaskEventTone {
  if (eventType === "approved" || eventType === "completed") return "success";
  if (eventType === "rejected" || eventType === "flagged") return "danger";
  if (eventType === "claimed" || eventType === "progress" || eventType === "requeued") return "running";
  return "neutral";
}

// A queued task sitting this long with nobody claiming it is worth flagging —
// the claim loop polls every ~15s, so anything past a couple minutes means
// something structural is blocking it, not just normal latency.
export const STALL_THRESHOLD_MINUTES = 2;

export type StallReason = "no-agent-with-skill" | "all-agents-paused" | "idle";

export interface StalledTask {
  task: Task;
  reason: StallReason;
  queuedMinutes: number;
}

// Surfaces the exact "queued tasks aging while agents sit idle" trap: a task
// with no assignee that's been queued past the threshold, bucketed by the
// most specific explanation we can derive client-side (no agent covers the
// skill at all; every agent that does is paused; otherwise a generic "idle"
// bucket — most often an unconfigured LLM provider key on the backend, which
// the frontend has no direct visibility into).
export function findStalledTasks(tasks: Task[], agents: Agent[], now: Date = new Date()): StalledTask[] {
  const stalled: StalledTask[] = [];
  for (const task of tasks) {
    if (task.status !== "queued") continue;
    const queuedMinutes = (now.getTime() - new Date(task.createdAt).getTime()) / 60000;
    if (queuedMinutes < STALL_THRESHOLD_MINUTES) continue;
    const covering = agents.filter((a) => a.skillTags.includes(task.requiredSkill));
    const reason: StallReason =
      covering.length === 0 ? "no-agent-with-skill" : covering.every((a) => a.paused) ? "all-agents-paused" : "idle";
    stalled.push({ task, reason, queuedMinutes: Math.floor(queuedMinutes) });
  }
  return stalled;
}

export interface StalledSummary {
  reason: StallReason;
  count: number;
  skills: string[];
}

// Groups stalled tasks by reason (+ distinct required skills) so the UI shows
// one banner with a few bullet points instead of one row per stuck task.
export function summarizeStalledTasks(stalled: StalledTask[]): StalledSummary[] {
  const byReason = new Map<StallReason, { count: number; skills: Set<string> }>();
  for (const { task, reason } of stalled) {
    const entry = byReason.get(reason) ?? { count: 0, skills: new Set<string>() };
    entry.count += 1;
    entry.skills.add(task.requiredSkill);
    byReason.set(reason, entry);
  }
  return Array.from(byReason.entries()).map(([reason, { count, skills }]) => ({
    reason,
    count,
    skills: Array.from(skills),
  }));
}

// One line per summary bucket — the exact copy shown in the stalled-tasks
// Banner on both Mission Control and Tasks, kept in one place so the two
// don't drift.
export function describeStalledSummary({ reason, count, skills }: StalledSummary): string {
  const taskWord = count === 1 ? "task" : "tasks";
  const skillList = skills.join(", ");
  if (reason === "no-agent-with-skill") {
    return `${count} ${taskWord} need a skill no agent has: ${skillList}.`;
  }
  if (reason === "all-agents-paused") {
    return `${count} ${taskWord} queued, but every agent with "${skillList}" is paused.`;
  }
  return `${count} ${taskWord} (skill: ${skillList}) queued ${STALL_THRESHOLD_MINUTES}+ min with no agent claiming them — if those agents use a paid LLM provider, confirm its API key is configured in the backend.`;
}

export interface OrgNode {
  agent: Agent;
  children: OrgNode[];
}

// MF-4: org chart tree from managerAgentId edges. Agents with no manager (or
// a manager not present in the roster) become roots.
export function orgTree(agents: Agent[]): OrgNode[] {
  const byManager = new Map<string | null, Agent[]>();
  const ids = new Set(agents.map((a) => a.id));
  for (const agent of agents) {
    const managerId = agent.managerAgentId && ids.has(agent.managerAgentId) ? agent.managerAgentId : null;
    const list = byManager.get(managerId) ?? [];
    list.push(agent);
    byManager.set(managerId, list);
  }
  const build = (managerId: string | null): OrgNode[] =>
    (byManager.get(managerId) ?? []).map((agent) => ({ agent, children: build(agent.id) }));
  return build(null);
}
