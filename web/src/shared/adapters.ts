// Maps core-api DTOs (shared/api.ts) onto the app's mock-shaped types
// (shared/types.ts) so every dashboard component keeps reading the same
// props whether the data came from mocks or the real API.
import type { AgentResponse, ArtifactResponse, BudgetResponse, SubtaskResponse, TaskResponse } from "./api";
import type { Agent, AgentStatus, Artifact, Budget, Subtask, Task, TaskStatus } from "./types";

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
    // No office_layout table yet (M2.4c) — officeLayout.ts falls back to the
    // status-zone mapping whenever locationKey doesn't match a real seat.
    locationKey: "",
    currentActivity: dto.currentActivity ?? "",
    about: dto.about,
    joinedAt: dto.joinedAt,
  };
}

export function adaptSubtask(dto: SubtaskResponse): Subtask {
  return { id: dto.id, label: dto.label, position: dto.position, state: dto.state as Subtask["state"] };
}

export function adaptArtifact(dto: ArtifactResponse): Artifact {
  return { kind: dto.kind as Artifact["kind"], content: dto.content };
}

// Base task fields available from the list endpoint. subtasks/artifact/
// flagReason/feedback are filled in by useEnrichedTasks (queries.ts) from
// the per-task detail + events calls — the list response doesn't carry them.
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
