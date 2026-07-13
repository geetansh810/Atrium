// React Query hooks over shared/api.ts — the real-API half of the M0.8 swap.
// Every hook here is scoped by companyId (03 invariant 4 has no bearing on the
// client, but the habit is worth keeping) and adapts DTOs to shared/types.ts
// shapes via shared/adapters.ts so components never see a raw API response.
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { adaptAgent, adaptArtifact, adaptBudget, adaptSubtask, adaptTaskBase } from "./adapters";
import type { Task } from "./types";

const POLL_MS = 4000;

export function currentPeriod(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
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
    };
  });

  return { tasks, isLoading: listQuery.isLoading };
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
    mutationFn: ({ agentId, status }: { agentId: string; status: string }) =>
      api.patchAgent(companyId, agentId, { status }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["roster", companyId] }),
  });
}

export function describeApiError(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong talking to core-api.";
}
