// Typed client for core-api (atrium-docs/04-api-contract.md + 16-api-contract-delta.md
// §1). Dev auth per 04 §Base URL: X-Company-Id header on every /api/v1 call except
// company creation. No X-User-Id — no Users API exists yet (Phase 3); omitting the
// header leaves tasks.created_by_user_id NULL rather than tripping the users FK.
import { API_BASE_URL, DEV_COMPANY_ID } from "./config";

export class ApiError extends Error {
  status: number;
  fieldErrors?: Record<string, string>;

  constructor(status: number, detail: string, fieldErrors?: Record<string, string>) {
    super(detail);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  companyId?: string;
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const companyId = opts.companyId ?? DEV_COMPANY_ID;
  if (companyId) headers["X-Company-Id"] = companyId;

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: opts.method ?? "GET",
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });

  if (!res.ok) {
    let detail = res.statusText;
    let fieldErrors: Record<string, string> | undefined;
    try {
      const problem = await res.json();
      detail = problem.detail ?? detail;
      fieldErrors = problem.fieldErrors;
    } catch {
      // non-JSON error body — fall back to statusText
    }
    throw new ApiError(res.status, detail, fieldErrors);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

// ── Shapes mirroring the Java DTOs verbatim (camelCase, so no reshape needed) ──

export interface CompanyResponse {
  id: string;
  name: string;
  slug: string;
  planTier: string;
  createdAt: string;
}

export interface AgentResponse {
  id: string;
  companyId: string;
  name: string;
  spriteKey: string;
  roleDefinitionId: string;
  roleTitle: string;
  skillTags: string[];
  modelProvider: string;
  modelName: string;
  managerAgentId: string | null;
  status: string;
  about: string;
  joinedAt: string;
  runtimeType: string;
  runtimeConfig: unknown;
  paused: boolean;
  currentActivity: string | null;
}

export interface HireAgentRequest {
  name: string;
  spriteKey?: string;
  roleDefinitionId?: string;
  roleTemplateKey?: string;
  roleTitle: string;
  skillTags: string[];
  modelProvider: string;
  modelName: string;
  managerAgentId?: string | null;
  about?: string;
}

export interface PatchAgentRequest {
  name?: string;
  spriteKey?: string;
  roleTitle?: string;
  skillTags?: string[];
  modelProvider?: string;
  modelName?: string;
  managerAgentId?: string | null;
  status?: string;
  about?: string;
  paused?: boolean;
}

export interface RoleDefinitionResponse {
  id: string;
  companyId: string | null;
  key: string;
  version: number;
  title: string;
  systemPrompt: string;
  allowedTools: unknown;
  outputContract: string | null;
  globalTemplate: boolean;
}

export interface ModelCatalogResponse {
  id: string;
  provider: string;
  modelName: string;
  displayName: string;
  tier: string;
  capabilities: unknown;
  contextWindow: number;
}

export interface TaskResponse {
  id: string;
  companyId: string;
  parentTaskId: string | null;
  requiredSkill: string;
  title: string;
  description: string;
  priority: number;
  status: string;
  progress: number;
  etaMinutes: number | null;
  assignedAgentId: string | null;
  createdByUserId: string | null;
  claimedAt: string | null;
  leaseExpiresAt: string | null;
  completedAt: string | null;
  createdAt: string;
  attempt: number;
  billingTaskId: string;
  requestDepth: number;
}

export interface SubtaskResponse {
  id: string;
  label: string;
  position: number;
  state: string;
}

export interface ArtifactResponse {
  id: string;
  kind: string;
  content: string;
  createdAt: string;
}

export interface TaskDetailResponse {
  task: TaskResponse;
  subtasks: SubtaskResponse[];
  latestArtifact: ArtifactResponse | null;
}

export interface TaskEventResponse {
  id: string;
  taskId: string;
  eventType: string;
  actor: string;
  payload: Record<string, unknown> | null;
  createdAt: string;
}

export interface PageEnvelope<T> {
  data: T[];
  nextCursor: string | null;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  requiredSkill: string;
  priority?: number;
  etaMinutes?: number | null;
  parentTaskId?: string;
  subtasks?: { label: string }[];
}

export interface BudgetResponse {
  id: string;
  agentId: string | null;
  period: string;
  capTokens: number;
  spentTokens: number;
  alertPct: number;
  alertedAt: string | null;
}

// GET /companies/{id}/analytics/* (04 §Accountability, M2.3)
export interface AnalyticsSummaryResponse {
  tasksCompletedToday: number;
  tasksApprovedToday: number;
  tasksRejectedToday: number;
  tokensSpentToday: number;
  costMicroUsdToday: number;
  successRateAllTime: number;
}

export interface DayCountResponse {
  day: string;
  count: number;
}

export interface AgentPerformanceResponse {
  agentId: string;
  tasksCompleted: number;
  tasksApproved: number;
  tasksRejected: number;
  successRate: number;
  tokensSpent: number;
  costMicroUsd: number;
}

export interface SkillShareResponse {
  skill: string;
  tasksCompleted: number;
  sharePct: number;
}

export interface TaskCostResponse {
  taskId: string;
  tokens: number;
  costMicroUsd: number;
}

export const api = {
  createCompany: (body: { name: string; slug: string }) =>
    request<CompanyResponse>("/companies", { method: "POST", body, companyId: "" }),
  getCompany: (id: string) => request<CompanyResponse>(`/companies/${id}`),

  hireAgent: (companyId: string, body: HireAgentRequest) =>
    request<AgentResponse>(`/companies/${companyId}/agents`, { method: "POST", body, companyId }),
  roster: (companyId: string) =>
    request<AgentResponse[]>(`/companies/${companyId}/roster`, { companyId }),
  patchAgent: (companyId: string, agentId: string, body: PatchAgentRequest) =>
    request<AgentResponse>(`/agents/${agentId}`, { method: "PATCH", body, companyId }),

  roleDefinitions: (companyId: string) =>
    request<RoleDefinitionResponse[]>("/role-definitions", { companyId }),

  modelCatalog: (companyId: string) =>
    request<ModelCatalogResponse[]>("/model-catalog", { companyId }),

  createTask: (companyId: string, body: CreateTaskRequest) =>
    request<TaskDetailResponse>(`/companies/${companyId}/tasks`, { method: "POST", body, companyId }),
  listTasks: (companyId: string, limit = 200) =>
    request<PageEnvelope<TaskResponse>>(`/companies/${companyId}/tasks?limit=${limit}`, { companyId }),
  getTask: (companyId: string, taskId: string) =>
    request<TaskDetailResponse>(`/tasks/${taskId}`, { companyId }),
  approveTask: (companyId: string, taskId: string) =>
    request<TaskResponse>(`/tasks/${taskId}/approve`, { method: "POST", companyId }),
  rejectTask: (companyId: string, taskId: string, feedback: string) =>
    request<TaskResponse>(`/tasks/${taskId}/reject`, { method: "POST", body: { feedback }, companyId }),
  taskEvents: (companyId: string, taskId: string, limit = 50) =>
    request<PageEnvelope<TaskEventResponse>>(`/tasks/${taskId}/events?limit=${limit}`, { companyId }),

  listBudgets: (companyId: string, period?: string) =>
    request<BudgetResponse[]>(
      `/companies/${companyId}/budget${period ? `?period=${period}` : ""}`,
      { companyId },
    ),
  upsertBudget: (companyId: string, body: { agentId: string | null; period: string; capTokens: number }) =>
    request<BudgetResponse>(`/companies/${companyId}/budget`, { method: "PUT", body, companyId }),

  analyticsSummary: (companyId: string) =>
    request<AnalyticsSummaryResponse>(`/companies/${companyId}/analytics/summary`, { companyId }),
  analyticsTasks7d: (companyId: string) =>
    request<DayCountResponse[]>(`/companies/${companyId}/analytics/tasks-7d`, { companyId }),
  analyticsAgentPerformance: (companyId: string, days?: number) =>
    request<AgentPerformanceResponse[]>(
      `/companies/${companyId}/analytics/agent-performance${days ? `?days=${days}` : ""}`,
      { companyId },
    ),
  analyticsTopSkills: (companyId: string, days?: number) =>
    request<SkillShareResponse[]>(
      `/companies/${companyId}/analytics/top-skills${days ? `?days=${days}` : ""}`,
      { companyId },
    ),
  analyticsCostPerTask: (companyId: string, period?: string, limit?: number) =>
    request<TaskCostResponse[]>(
      `/companies/${companyId}/analytics/cost-per-task?${new URLSearchParams({
        ...(period ? { period } : {}),
        ...(limit ? { limit: String(limit) } : {}),
      }).toString()}`,
      { companyId },
    ),
};
