// Maps core-api DTOs (shared/api.ts) onto the app's mock-shaped types
// (shared/types.ts) so every dashboard component keeps reading the same
// props whether the data came from mocks or the real API.
import type {
  AgentPerformanceResponse,
  AgentResponse,
  AnalyticsSummaryResponse,
  AnnouncementResponse,
  ArtifactResponse,
  BudgetResponse,
  ChannelResponse,
  DayCountResponse,
  MessageResponse,
  SkillShareResponse,
  SubtaskResponse,
  TaskCostResponse,
  TaskEventResponse,
  TaskResponse,
} from "./api";
import type {
  AgentPerformance,
  AgentStatus,
  Agent,
  AnalyticsSummaryReal,
  Announcement,
  AnnouncementCategory,
  Artifact,
  Budget,
  Channel,
  ChatMessage,
  DayCount,
  SkillShare,
  Subtask,
  Task,
  TaskCost,
  TaskEvent,
  TaskStatus,
} from "./types";

export function adaptAgent(dto: AgentResponse): Agent {
  return {
    id: dto.id,
    name: dto.name,
    spriteKey: dto.spriteKey,
    roleTitle: dto.roleTitle,
    skillTags: dto.skillTags,
    modelProvider: (dto.modelProvider as Agent["modelProvider"]) ?? "anthropic",
    modelName: dto.modelName,
    managerAgentId: dto.managerAgentId,
    status: dto.status as AgentStatus,
    // core-api doesn't track a status-changed timestamp yet (agent.status_changed
    // isn't published anywhere — 12 §4 taxonomy exists, nothing calls it yet).
    // joinedAt is the closest honest stand-in.
    statusSince: dto.joinedAt,
    currentActivity: dto.currentActivity ?? "",
    about: dto.about,
    joinedAt: dto.joinedAt,
    paused: dto.paused,
  };
}

export function adaptSubtask(dto: SubtaskResponse): Subtask {
  return { id: dto.id, label: dto.label, position: dto.position, state: dto.state as Subtask["state"] };
}

export function adaptArtifact(dto: ArtifactResponse): Artifact {
  return { kind: dto.kind as Artifact["kind"], content: dto.content };
}

export function adaptTaskEvent(dto: TaskEventResponse): TaskEvent {
  return { id: dto.id, eventType: dto.eventType, actor: dto.actor, payload: dto.payload, createdAt: dto.createdAt };
}

// Base task fields available from the list endpoint. subtasks/artifact/
// flagReason/feedback/events are filled in by useEnrichedTasks (queries.ts)
// from the per-task detail + events calls — the list response doesn't carry them.
export function adaptTaskBase(dto: TaskResponse): Task {
  return {
    id: dto.id,
    parentTaskId: dto.parentTaskId,
    title: dto.title,
    description: dto.description,
    requiredSkill: dto.requiredSkill,
    priority: dto.priority,
    status: dto.status as TaskStatus,
    progress: dto.progress,
    etaMinutes: dto.etaMinutes,
    assignedAgentId: dto.assignedAgentId,
    createdAt: dto.createdAt,
    completedAt: dto.completedAt,
    subtasks: [],
    artifact: null,
    flagReason: null,
    feedback: null,
    events: [],
  };
}

export function adaptBudget(dto: BudgetResponse): Budget {
  return {
    id: dto.id,
    agentId: dto.agentId,
    period: dto.period,
    capTokens: dto.capTokens,
    spentTokens: dto.spentTokens,
  };
}

const MICRO_USD_PER_USD = 1_000_000;

export function adaptAnalyticsSummary(dto: AnalyticsSummaryResponse): AnalyticsSummaryReal {
  return {
    tasksCompletedToday: dto.tasksCompletedToday,
    tasksApprovedToday: dto.tasksApprovedToday,
    tasksRejectedToday: dto.tasksRejectedToday,
    tokensSpentToday: dto.tokensSpentToday,
    costUsdToday: dto.costMicroUsdToday / MICRO_USD_PER_USD,
    successRateAllTime: dto.successRateAllTime,
  };
}

export function adaptDayCount(dto: DayCountResponse): DayCount {
  return { day: dto.day, count: dto.count };
}

export function adaptAgentPerformance(dto: AgentPerformanceResponse): AgentPerformance {
  return {
    agentId: dto.agentId,
    successRate: dto.successRate,
    tasksCompleted: dto.tasksCompleted,
    tasksApproved: dto.tasksApproved,
    tasksRejected: dto.tasksRejected,
    tokensSpent: dto.tokensSpent,
    costUsd: dto.costMicroUsd / MICRO_USD_PER_USD,
  };
}

export function adaptSkillShare(dto: SkillShareResponse): SkillShare {
  return { skill: dto.skill, sharePct: dto.sharePct, tasksCompleted: dto.tasksCompleted };
}

export function adaptTaskCost(dto: TaskCostResponse): TaskCost {
  return { taskId: dto.taskId, tokens: dto.tokens, costUsd: dto.costMicroUsd / MICRO_USD_PER_USD };
}

export function adaptChannel(dto: ChannelResponse): Channel {
  // Real channels carry no agent link column, so DMs (none created in API mode
  // yet) resolve agentId to null — the frontend Channel.kind union still holds.
  return {
    id: dto.id,
    name: dto.name,
    kind: dto.kind === "dm" ? "dm" : "channel",
    agentId: null,
  };
}

export function adaptMessage(dto: MessageResponse): ChatMessage {
  return { id: dto.id, channelId: dto.channelId, sender: dto.sender, text: dto.text, createdAt: dto.createdAt };
}

export function adaptAnnouncement(dto: AnnouncementResponse): Announcement {
  return {
    id: dto.id,
    title: dto.title,
    body: dto.body,
    category: (["company", "update", "maintenance"].includes(dto.category)
      ? dto.category
      : "company") as AnnouncementCategory,
    createdAt: dto.createdAt,
  };
}
