// React Query hooks over shared/api.ts — the real-API half of the M0.8 swap.
// Every hook here is scoped by companyId (03 invariant 4 has no bearing on the
// client, but the habit is worth keeping) and adapts DTOs to shared/types.ts
// shapes via shared/adapters.ts so components never see a raw API response.
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import {
  adaptAgent,
  adaptAgentPerformance,
  adaptAnalyticsSummary,
  adaptAnnouncement,
  adaptArtifact,
  adaptBudget,
  adaptChannel,
  adaptDayCount,
  adaptMemory,
  adaptMessage,
  adaptSkillShare,
  adaptSubtask,
  adaptTaskBase,
  adaptTaskCost,
  adaptTaskEvent,
} from "./adapters";
import type { Task } from "./types";

const POLL_MS = 4000;

export function currentPeriod(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export function useCompany(companyId: string) {
  return useQuery({
    queryKey: ["company", companyId],
    queryFn: () => api.getCompany(companyId),
    enabled: !!companyId,
  });
}

export function useModelCatalog(companyId: string) {
  return useQuery({
    queryKey: ["model-catalog", companyId],
    queryFn: () => api.modelCatalog(companyId),
    enabled: !!companyId,
  });
}

export function useRoster(companyId: string) {
  return useQuery({
    queryKey: ["roster", companyId],
    queryFn: () => api.roster(companyId).then((list) => list.map(adaptAgent)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

export function useBudgets(companyId: string) {
  return useQuery({
    queryKey: ["budgets", companyId, currentPeriod()],
    queryFn: () => api.listBudgets(companyId, currentPeriod()).then((list) => list.map(adaptBudget)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

// GET /companies/{id}/budget?period= for an explicit (possibly non-current)
// period — the Budget Ledger period selector (M2.3, OrganizationPage's
// Payroll tab). useBudgets above stays current-period-only for every other consumer.
export function useBudgetsForPeriod(companyId: string, period: string) {
  return useQuery({
    queryKey: ["budgets", companyId, period],
    queryFn: () => api.listBudgets(companyId, period).then((list) => list.map(adaptBudget)),
    enabled: !!companyId && !!period,
  });
}

// GET /companies/{id}/analytics/* (M2.3) — read-only, page-scoped, deliberately
// NOT part of AppState/Action (same "net-new data outside the store" precedent
// MF-5's shared/domains/*.ts and MF-6's useCompany/useModelCatalog set).
export function useAnalyticsSummary(companyId: string) {
  return useQuery({
    queryKey: ["analytics-summary", companyId],
    queryFn: () => api.analyticsSummary(companyId).then(adaptAnalyticsSummary),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

export function useTasks7d(companyId: string) {
  return useQuery({
    queryKey: ["analytics-tasks-7d", companyId],
    queryFn: () => api.analyticsTasks7d(companyId).then((list) => list.map(adaptDayCount)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

export function useAgentPerformance(companyId: string, days = 7) {
  return useQuery({
    queryKey: ["analytics-agent-performance", companyId, days],
    queryFn: () => api.analyticsAgentPerformance(companyId, days).then((list) => list.map(adaptAgentPerformance)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

export function useTopSkills(companyId: string, days = 7) {
  return useQuery({
    queryKey: ["analytics-top-skills", companyId, days],
    queryFn: () => api.analyticsTopSkills(companyId, days).then((list) => list.map(adaptSkillShare)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

export function useAgentMemories(companyId: string, agentId: string) {
  return useQuery({
    queryKey: ["memories", companyId, agentId],
    queryFn: () => api.listMemories(companyId, agentId).then((page) => page.data.map(adaptMemory)),
    enabled: !!companyId && !!agentId,
    refetchInterval: POLL_MS,
  });
}

export function useCostPerTask(companyId: string, period: string) {
  return useQuery({
    queryKey: ["analytics-cost-per-task", companyId, period],
    queryFn: () => api.analyticsCostPerTask(companyId, period).then((list) => list.map(adaptTaskCost)),
    enabled: !!companyId && !!period,
  });
}

// Task list (compact) + per-task detail/events, merged into full mock-shaped
// Task objects. Fine at demo scale (a handful of tasks); would need a fatter
// list DTO before this scales past a few dozen open tasks.
export function useEnrichedTasks(companyId: string): { tasks: Task[]; isLoading: boolean } {
  const listQuery = useQuery({
    queryKey: ["tasks", companyId],
    queryFn: () => api.listTasks(companyId, 200).then((env) => env.data.map(adaptTaskBase)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
  const baseTasks = listQuery.data ?? [];

  const detailQueries = useQueries({
    queries: baseTasks.map((t) => ({
      queryKey: ["task-detail", companyId, t.id],
      queryFn: () => api.getTask(companyId, t.id),
      enabled: !!companyId,
      refetchInterval: POLL_MS,
    })),
  });
  const eventQueries = useQueries({
    queries: baseTasks.map((t) => ({
      queryKey: ["task-events", companyId, t.id],
      queryFn: () => api.taskEvents(companyId, t.id, 50),
      enabled: !!companyId,
      refetchInterval: POLL_MS,
    })),
  });

  const tasks: Task[] = baseTasks.map((base, i) => {
    const detail = detailQueries[i]?.data;
    const events = eventQueries[i]?.data?.data ?? [];
    const flagged = events.findLast((e) => e.eventType === "flagged");
    const rejected = events.findLast((e) => e.eventType === "rejected");
    return {
      ...base,
      subtasks: detail?.subtasks.map(adaptSubtask) ?? [],
      artifact: detail?.latestArtifact ? adaptArtifact(detail.latestArtifact) : null,
      flagReason: (flagged?.payload?.reason as string | undefined) ?? null,
      feedback: (rejected?.payload?.feedback as string | undefined) ?? null,
      events: events.map(adaptTaskEvent),
    };
  });

  return { tasks, isLoading: listQuery.isLoading };
}

// ── Communication (M2.5): channels, messages, announcements ──────────────────

export function useChannels(companyId: string) {
  return useQuery({
    queryKey: ["channels", companyId],
    queryFn: () => api.listChannels(companyId).then((list) => list.map(adaptChannel)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

// Messages for ONE channel — ConversationThread filters state.messages by
// channelId anyway, so loading only the active channel's history is enough.
// Returned oldest-first to match the mock's ascending order.
export function useChannelMessages(companyId: string, channelId: string | undefined) {
  return useQuery({
    queryKey: ["messages", companyId, channelId],
    queryFn: () =>
      api.listMessages(companyId, channelId as string).then((page) => page.data.map(adaptMessage).reverse()),
    enabled: !!companyId && !!channelId,
    refetchInterval: POLL_MS,
  });
}

export function useSendMessageMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ channelId, text }: { channelId: string; text: string }) =>
      api.sendMessage(companyId, channelId, text),
    onSuccess: (_r, { channelId }) =>
      qc.invalidateQueries({ queryKey: ["messages", companyId, channelId] }),
  });
}

export function useAnnouncements(companyId: string) {
  return useQuery({
    queryKey: ["announcements", companyId],
    queryFn: () => api.listAnnouncements(companyId).then((list) => list.map(adaptAnnouncement)),
    enabled: !!companyId,
    refetchInterval: POLL_MS,
  });
}

export function useAddAnnouncementMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { title: string; body: string; category: string }) =>
      api.createAnnouncement(companyId, {
        title: input.title,
        body: input.body || undefined,
        category: input.category,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["announcements", companyId] }),
  });
}

export function useCreateTaskMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { title: string; description: string; requiredSkill: string; priority: number; etaMinutes: number | null }) =>
      api.createTask(companyId, {
        title: input.title,
        description: input.description || undefined,
        requiredSkill: input.requiredSkill,
        priority: input.priority,
        etaMinutes: input.etaMinutes ?? undefined,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tasks", companyId] }),
  });
}

export function useApproveTaskMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => api.approveTask(companyId, taskId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["tasks", companyId] });
      qc.invalidateQueries({ queryKey: ["budgets", companyId] });
    },
  });
}

export function useRejectTaskMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, feedback }: { taskId: string; feedback: string }) =>
      api.rejectTask(companyId, taskId, feedback),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tasks", companyId] }),
  });
}

export function useHireAgentMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      roleTemplateKey: string;
      name: string;
      roleTitle: string;
      skillTags: string[];
      modelProvider: string;
      modelName: string;
      managerAgentId: string | null;
      about: string;
      budgetTokens: number;
    }) => {
      const agent = await api.hireAgent(companyId, {
        name: input.name,
        roleTemplateKey: input.roleTemplateKey,
        roleTitle: input.roleTitle,
        skillTags: input.skillTags,
        modelProvider: input.modelProvider,
        modelName: input.modelName,
        managerAgentId: input.managerAgentId,
        about: input.about,
      });
      await api.upsertBudget(companyId, {
        agentId: agent.id,
        period: currentPeriod(),
        capTokens: input.budgetTokens,
      });
      await api.patchAgent(companyId, agent.id, { status: "online" });
      return agent;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["roster", companyId] });
      qc.invalidateQueries({ queryKey: ["budgets", companyId] });
    },
  });
}

export function useSetBudgetCapMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { agentId: string | null; period: string; capTokens: number }) =>
      api.upsertBudget(companyId, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["budgets", companyId] }),
  });
}

export function usePatchAgentMutation(companyId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ agentId, status, paused }: { agentId: string; status?: string; paused?: boolean }) =>
      api.patchAgent(companyId, agentId, { status, paused }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["roster", companyId] }),
  });
}

export function describeApiError(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong talking to core-api.";
}
