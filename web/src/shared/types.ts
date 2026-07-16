// Mirrors atrium-docs/03-data-model.md — field names/shapes match the DB columns
// (camelCase in API/JSON per 08-conventions.md) so this can point at the real
// API later without a reshape.

export type AgentStatus =
  | "online"
  | "working"
  | "in_meeting"
  | "in_focus"
  | "away"
  | "offline";

export type ModelProvider = "anthropic" | "openai" | "google";

export interface Agent {
  id: string;
  name: string;
  spriteKey: string;
  roleTitle: string;
  skillTags: string[];
  modelProvider: ModelProvider;
  modelName: string;
  managerAgentId: string | null;
  status: AgentStatus;
  statusSince: string;
  currentActivity: string;
  about: string;
  joinedAt: string;
  paused: boolean;
}

// From agent_stats_daily rollup — served by GET /agents/{id}/profile
export interface AgentStats {
  tasksCompleted: number;
  successRate: number;
  focusMinutes: number;
}

export type TaskStatus =
  | "queued"
  | "claimed"
  | "in_progress"
  | "flagged"
  | "pending_review"
  | "approved"
  | "rejected"
  | "cancelled";

export type SubtaskState = "todo" | "doing" | "done";

export interface Subtask {
  id: string;
  label: string;
  position: number;
  state: SubtaskState;
}

export interface Artifact {
  kind: "text" | "diff" | "file" | "report" | "json";
  content: string;
}

// Mirrors GET /tasks/{id}/events (task_events row) — powers the task timeline,
// the Live Activity Feed, and workflow node states (MF-2).
export interface TaskEvent {
  id: string;
  eventType: string;
  actor: string;
  payload: Record<string, unknown> | null;
  createdAt: string;
}

export interface Task {
  id: string;
  parentTaskId: string | null;
  title: string;
  description: string;
  requiredSkill: string;
  priority: number; // 1 high … 5 low
  status: TaskStatus;
  progress: number;
  etaMinutes: number | null;
  assignedAgentId: string | null;
  createdAt: string;
  completedAt: string | null;
  subtasks: Subtask[];
  artifact: Artifact | null;
  flagReason: string | null;
  feedback: string | null;
  events: TaskEvent[];
}

// Pre-rendered from task_events + presence events (Agent Profile activity feed)
export interface ActivityEvent {
  id: string;
  agentId: string;
  text: string;
  createdAt: string;
}

export interface Channel {
  id: string;
  name: string;
  kind: "channel" | "dm";
  agentId: string | null; // set for DMs
}

export interface ChatMessage {
  id: string;
  channelId: string;
  sender: string; // 'user:<id>' | 'agent:<id>' | 'bot'
  text: string;
  createdAt: string;
}

export type AnnouncementCategory = "company" | "update" | "maintenance";

export interface Announcement {
  id: string;
  title: string;
  body: string | null;
  category: AnnouncementCategory;
  createdAt: string;
}

export interface AnalyticsSummary {
  totalAgents: number;
  totalAgentsDelta: string;
  tasksCompleted: number;
  tasksCompletedDelta: string;
  avgSuccessRate: number;
  avgSuccessRateDelta: string;
  focusMinutesToday: number;
  focusMinutesDelta: string;
}

export interface DayCount {
  day: string;
  count: number;
}

export interface AgentPerformance {
  agentId: string;
  successRate: number;
  // Real fields since M2.3 (agent_stats_daily) — optional so the pre-M2.3 mock
  // fixture (successRate only) still type-checks.
  tasksCompleted?: number;
  tasksApproved?: number;
  tasksRejected?: number;
  tokensSpent?: number;
  costUsd?: number;
}

export interface SkillShare {
  skill: string;
  sharePct: number;
  tasksCompleted?: number; // real since M2.3
}

// GET /companies/{id}/analytics/summary (M2.3) — real KPI totals, distinct
// from the mock-only AnalyticsSummary above (which carries invented trend
// deltas no real endpoint computes).
export interface AnalyticsSummaryReal {
  tasksCompletedToday: number;
  tasksApprovedToday: number;
  tasksRejectedToday: number;
  tokensSpentToday: number;
  costUsdToday: number;
  successRateAllTime: number;
}

export interface Budget {
  id: string;
  agentId: string | null; // null = company-wide cap
  period: string; // 'YYYY-MM'
  capTokens: number;
  spentTokens: number;
}

export interface TaskCost {
  taskId: string;
  tokens: number;
  costUsd: number;
}

export interface RoleTemplate {
  key: string;
  title: string;
  description: string;
  skillTags: string[];
  modelProvider: ModelProvider;
  modelName: string;
  defaultBudgetTokens: number;
}

export interface CurrentUser {
  id: string;
  displayName: string;
}

// --- MF-5: net-new mock-only domains (shared/domains/*.ts) — no backend
// equivalent exists yet, so these are never gated by VITE_USE_MOCKS. Real
// task ids may still be referenced from them (see Project.taskLinks usage
// in shared/domains/projects.ts), but the domains themselves stay fixture-only
// until their own backend milestone lands.

export type ProjectStatus = "planning" | "active" | "on_hold" | "completed";

// no backend yet — domain hook only
export interface Project {
  id: string;
  name: string;
  description: string;
  status: ProjectStatus;
  ownerAgentId: string | null;
  budgetCapTokens: number | null;
  createdAt: string;
}

// no backend yet — domain hook only
export interface KnowledgeDoc {
  id: string;
  title: string;
  excerpt: string;
  body: string;
  tags: string[];
  updatedAt: string;
}

// no backend yet — domain hook only
export interface WorkflowTemplate {
  id: string;
  name: string;
  description: string;
  steps: string[];
}

// GET /companies/{id}/memories (16 §3, M-MEM1/M-LN1) — real, API-only, no mock fixture.
export interface Memory {
  id: string;
  scope: string;
  agentId: string | null;
  roleKey: string | null;
  taskId: string | null;
  kind: string;
  content: string;
  importance: number;
  status: string;
  useCount: number;
  lastUsedAt: string | null;
  createdAt: string;
}
