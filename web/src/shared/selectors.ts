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
