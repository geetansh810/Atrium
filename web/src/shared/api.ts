// Typed client for core-api (atrium-docs/04-api-contract.md + 16-api-contract-delta.md
// §1). Auth (M3.1): every /api/v1 call carries Authorization: Bearer <token> from
// the signed-in session (shared/auth.ts) — the old X-Company-Id/X-User-Id dev
// headers are gone. auth.signup/auth.login are the only pre-auth calls.
import { API_BASE_URL } from "./config";
import { getAuthSession } from "./auth";

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
  /** Only auth.signup/auth.login pass this — every other call authenticates via the stored session. */
  skipAuth?: boolean;
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (!opts.skipAuth) {
    const token = getAuthSession()?.token;
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

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

export interface AuthResponse {
  token: string;
  companyId: string;
  companyName: string;
  companySlug: string;
  userId: string;
  displayName: string;
  role: string;
}

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

// GET/POST /companies/{id}/channels, /channels/{id}/messages (04 §Communication, M2.5)
export interface ChannelResponse {
  id: string;
  companyId: string;
  name: string;
  kind: string;
  createdAt: string;
}

export interface MessageResponse {
  id: string;
  channelId: string;
  sender: string;
  text: string;
  createdAt: string;
}

export interface AnnouncementResponse {
  id: string;
  title: string;
  body: string | null;
  category: string;
  createdAt: string;
}

// GET /companies/{id}/memories (16 §3, M-MEM1/M-LN1)
export interface MemoryResponse {
  id: string;
  companyId: string;
  scope: string;
  agentId: string | null;
  roleKey: string | null;
  taskId: string | null;
  kind: string;
  content: string;
  importance: number;
  status: string;
  provenance: unknown;
  useCount: number;
  lastUsedAt: string | null;
  createdAt: string;
}

export const api = {
  // M3.1: the only two pre-auth calls — everything else authenticates via the
  // stored session's bearer token (shared/auth.ts), never a companyId header.
  signup: (body: { companyName: string; companySlug: string; displayName: string; email: string; password: string }) =>
    request<AuthResponse>("/auth/signup", { method: "POST", body, skipAuth: true }),
  login: (body: { email: string; password: string }) =>
    request<AuthResponse>("/auth/login", { method: "POST", body, skipAuth: true }),

  getCompany: (id: string) => request<CompanyResponse>(`/companies/${id}`),

  hireAgent: (companyId: string, body: HireAgentRequest) =>
    request<AgentResponse>(`/companies/${companyId}/agents`, { method: "POST", body }),
  roster: (companyId: string) => request<AgentResponse[]>(`/companies/${companyId}/roster`),
  patchAgent: (_companyId: string, agentId: string, body: PatchAgentRequest) =>
    request<AgentResponse>(`/agents/${agentId}`, { method: "PATCH", body }),

  roleDefinitions: () => request<RoleDefinitionResponse[]>("/role-definitions"),

  modelCatalog: (_companyId: string) => request<ModelCatalogResponse[]>("/model-catalog"),

  createTask: (companyId: string, body: CreateTaskRequest) =>
    request<TaskDetailResponse>(`/companies/${companyId}/tasks`, { method: "POST", body }),
  listTasks: (companyId: string, limit = 200) =>
    request<PageEnvelope<TaskResponse>>(`/companies/${companyId}/tasks?limit=${limit}`),
  getTask: (_companyId: string, taskId: string) => request<TaskDetailResponse>(`/tasks/${taskId}`),
  approveTask: (_companyId: string, taskId: string) =>
    request<TaskResponse>(`/tasks/${taskId}/approve`, { method: "POST" }),
  rejectTask: (_companyId: string, taskId: string, feedback: string) =>
    request<TaskResponse>(`/tasks/${taskId}/reject`, { method: "POST", body: { feedback } }),
  taskEvents: (_companyId: string, taskId: string, limit = 50) =>
    request<PageEnvelope<TaskEventResponse>>(`/tasks/${taskId}/events?limit=${limit}`),

  listBudgets: (companyId: string, period?: string) =>
    request<BudgetResponse[]>(`/companies/${companyId}/budget${period ? `?period=${period}` : ""}`),
  upsertBudget: (companyId: string, body: { agentId: string | null; period: string; capTokens: number }) =>
    request<BudgetResponse>(`/companies/${companyId}/budget`, { method: "PUT", body }),

  analyticsSummary: (companyId: string) =>
    request<AnalyticsSummaryResponse>(`/companies/${companyId}/analytics/summary`),
  analyticsTasks7d: (companyId: string) =>
    request<DayCountResponse[]>(`/companies/${companyId}/analytics/tasks-7d`),
  analyticsAgentPerformance: (companyId: string, days?: number) =>
    request<AgentPerformanceResponse[]>(
      `/companies/${companyId}/analytics/agent-performance${days ? `?days=${days}` : ""}`,
    ),
  analyticsTopSkills: (companyId: string, days?: number) =>
    request<SkillShareResponse[]>(
      `/companies/${companyId}/analytics/top-skills${days ? `?days=${days}` : ""}`,
    ),
  analyticsCostPerTask: (companyId: string, period?: string, limit?: number) =>
    request<TaskCostResponse[]>(
      `/companies/${companyId}/analytics/cost-per-task?${new URLSearchParams({
        ...(period ? { period } : {}),
        ...(limit ? { limit: String(limit) } : {}),
      }).toString()}`,
    ),

  listChannels: (companyId: string) => request<ChannelResponse[]>(`/companies/${companyId}/channels`),
  createChannel: (companyId: string, body: { name: string; kind?: string }) =>
    request<ChannelResponse>(`/companies/${companyId}/channels`, { method: "POST", body }),
  listMessages: (_companyId: string, channelId: string, limit = 50) =>
    request<PageEnvelope<MessageResponse>>(`/channels/${channelId}/messages?limit=${limit}`),
  sendMessage: (_companyId: string, channelId: string, text: string) =>
    request<MessageResponse>(`/channels/${channelId}/messages`, { method: "POST", body: { text } }),

  listAnnouncements: (companyId: string) =>
    request<AnnouncementResponse[]>(`/companies/${companyId}/announcements`),
  createAnnouncement: (companyId: string, body: { title: string; body?: string; category?: string }) =>
    request<AnnouncementResponse>(`/companies/${companyId}/announcements`, { method: "POST", body }),

  listEscalations: (companyId: string) => request<TaskResponse[]>(`/companies/${companyId}/escalations`),

  listMemories: (companyId: string, agentId: string) =>
    request<PageEnvelope<MemoryResponse>>(
      `/companies/${companyId}/memories?${new URLSearchParams({ agentId }).toString()}`,
    ),
  memoryReviewQueue: (companyId: string) =>
    request<PageEnvelope<MemoryResponse>>(`/companies/${companyId}/memories/review-queue`),
  reviewMemory: (_companyId: string, memoryId: string, body: { action: "approve" | "reject" }) =>
    request<MemoryResponse>(`/memories/${memoryId}/review`, { method: "POST", body }),
};
