// Shared shapes between shared/mockStore.tsx and shared/apiStore.tsx — split out
// so both providers implement the exact same AppState/Action contract components
// depend on (shared/store.tsx picks the provider at module load, per config.USE_MOCKS).
import type {
  ActivityEvent,
  Agent,
  AgentStats,
  AnnouncementCategory,
  Announcement,
  Budget,
  Channel,
  ChatMessage,
  ModelProvider,
  RoleTemplate,
  Task,
} from "./types";

export type PanelKey = "analytics" | "chat" | "announcements" | "flow";

export interface UiState {
  activePanel: PanelKey | null;
  selectedAgentId: string | null;
  selectedTaskId: string | null;
  activeChannelId: string;
  activeRoom: string;
  railVisible: boolean;
  botNotice: string | null;
  inviteOpen: boolean;
  newTaskOpen: boolean;
}

export const initialUiState: UiState = {
  activePanel: null,
  selectedAgentId: null,
  selectedTaskId: null,
  activeChannelId: "ch-general",
  activeRoom: "Lobby",
  railVisible: true,
  botNotice: null,
  inviteOpen: false,
  newTaskOpen: false,
};

export interface AppState {
  agents: Agent[];
  agentStats: Record<string, AgentStats>;
  tasks: Task[];
  activity: ActivityEvent[];
  channels: Channel[];
  messages: ChatMessage[];
  announcements: Announcement[];
  budgets: Budget[];
  roleTemplates: RoleTemplate[];
  ui: UiState;
}

export interface NewTaskInput {
  title: string;
  description: string;
  requiredSkill: string;
  priority: number;
  etaMinutes: number | null;
}

export interface InviteAgentInput {
  name: string;
  roleTitle: string;
  skillTags: string[];
  modelProvider: ModelProvider;
  modelName: string;
  managerAgentId: string | null;
  about: string;
  budgetTokens: number;
  // Present when the pick came from a template card — apiStore needs the key
  // to call POST /agents with roleTemplateKey; mockStore ignores it.
  roleTemplateKey?: string | null;
}

export type Action =
  | { type: "openPanel"; panel: PanelKey; agentId?: string; taskId?: string; channelId?: string }
  | { type: "closePanel" }
  | { type: "setRoom"; room: string }
  | { type: "toggleRail" }
  | { type: "setChannel"; channelId: string }
  | { type: "sendMessage"; channelId: string; sender: string; text: string }
  | { type: "createTask"; input: NewTaskInput }
  | { type: "toggleSubtask"; taskId: string; subtaskId: string }
  | { type: "approveTask"; taskId: string }
  | { type: "rejectTask"; taskId: string; feedback: string }
  | { type: "inviteAgent"; input: InviteAgentInput }
  | { type: "addAnnouncement"; title: string; body: string; category: AnnouncementCategory }
  | { type: "setBudgetCap"; budgetId: string; capTokens: number }
  | { type: "exitFocusPod"; agentId: string }
  | { type: "setAgentPaused"; agentId: string; paused: boolean }
  | { type: "setNotice"; text: string }
  | { type: "dismissNotice" }
  | { type: "setInviteOpen"; open: boolean }
  | { type: "setNewTaskOpen"; open: boolean };

export function escalationCount(tasks: Task[]): number {
  return tasks.filter((t) => t.status === "flagged" || t.status === "pending_review").length;
}

let idCounter = 0;
export function makeId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-${Date.now()}-${idCounter}`;
}

export const nowIso = () => new Date().toISOString();
