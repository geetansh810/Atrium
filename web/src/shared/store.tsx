// App-wide state over the mock fixtures. This reducer is the stand-in for the
// real API mutations (POST /tasks, /approve, /reject, /agents, …) — when
// core-api arrives, actions here become React Query mutations 1:1.
import { createContext, useContext, useReducer } from "react";
import type { Dispatch, ReactNode } from "react";
import * as mock from "./mockData";
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
  Task,
} from "./types";

export type PanelKey =
  | "profile"
  | "workspace"
  | "analytics"
  | "chat"
  | "announcements"
  | "flow"
  | "approvals"
  | "budget"
  | "focus";

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

export interface AppState {
  agents: Agent[];
  agentStats: Record<string, AgentStats>;
  tasks: Task[];
  activity: ActivityEvent[];
  channels: Channel[];
  messages: ChatMessage[];
  announcements: Announcement[];
  budgets: Budget[];
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
  | { type: "setNotice"; text: string }
  | { type: "dismissNotice" }
  | { type: "setInviteOpen"; open: boolean }
  | { type: "setNewTaskOpen"; open: boolean };

const initialState: AppState = {
  agents: mock.agents,
  agentStats: mock.agentStats,
  tasks: mock.tasks,
  activity: mock.activityEvents,
  channels: mock.channels,
  messages: mock.messages,
  announcements: mock.announcements,
  budgets: mock.budgets,
  ui: {
    activePanel: null,
    selectedAgentId: null,
    selectedTaskId: null,
    activeChannelId: "ch-general",
    activeRoom: "Lobby",
    railVisible: true,
    botNotice: null,
    inviteOpen: false,
    newTaskOpen: false,
  },
};

let idCounter = 0;
function makeId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-${Date.now()}-${idCounter}`;
}

const nowIso = () => new Date().toISOString();

function withEvent(activity: ActivityEvent[], agentId: string, text: string): ActivityEvent[] {
  return [{ id: makeId("ev"), agentId, text, createdAt: nowIso() }, ...activity];
}

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case "openPanel":
      return {
        ...state,
        ui: {
          ...state.ui,
          activePanel: action.panel,
          selectedAgentId: action.agentId ?? state.ui.selectedAgentId,
          selectedTaskId: action.taskId ?? state.ui.selectedTaskId,
          activeChannelId: action.channelId ?? state.ui.activeChannelId,
        },
      };
    case "closePanel":
      return { ...state, ui: { ...state.ui, activePanel: null } };
    case "setRoom":
      return { ...state, ui: { ...state.ui, activeRoom: action.room } };
    case "toggleRail":
      return { ...state, ui: { ...state.ui, railVisible: !state.ui.railVisible } };
    case "setChannel":
      return { ...state, ui: { ...state.ui, activeChannelId: action.channelId } };

    case "sendMessage": {
      const message: ChatMessage = {
        id: makeId("msg"),
        channelId: action.channelId,
        sender: action.sender,
        text: action.text,
        createdAt: nowIso(),
      };
      return { ...state, messages: [...state.messages, message] };
    }

    case "createTask": {
      const assignee =
        state.agents.find((a) => a.skillTags.includes(action.input.requiredSkill)) ?? null;
      const task: Task = {
        id: makeId("task"),
        parentTaskId: null,
        title: action.input.title,
        description: action.input.description,
        requiredSkill: action.input.requiredSkill,
        priority: action.input.priority,
        status: "queued",
        progress: 0,
        etaMinutes: action.input.etaMinutes,
        assignedAgentId: assignee?.id ?? null,
        createdAt: nowIso(),
        completedAt: null,
        subtasks: [],
        artifact: null,
        flagReason: null,
        feedback: null,
      };
      return {
        ...state,
        tasks: [task, ...state.tasks],
        activity: assignee
          ? withEvent(state.activity, assignee.id, `Queued: ${task.title}`)
          : state.activity,
        ui: {
          ...state.ui,
          newTaskOpen: false,
          botNotice: assignee
            ? `Task "${task.title}" queued for ${assignee.name}.`
            : `Task "${task.title}" queued — no agent has the skill "${task.requiredSkill}" yet.`,
        },
      };
    }

    case "toggleSubtask": {
      let noticeAgentId: string | null = null;
      let noticeTitle = "";
      const tasks = state.tasks.map((task) => {
        if (task.id !== action.taskId) return task;
        const subtasks = task.subtasks.map((s) =>
          s.id === action.subtaskId
            ? { ...s, state: s.state === "done" ? ("todo" as const) : ("done" as const) }
            : s,
        );
        const done = subtasks.filter((s) => s.state === "done").length;
        const progress = subtasks.length ? Math.round((done / subtasks.length) * 100) : task.progress;
        const allDone = subtasks.length > 0 && done === subtasks.length;
        const movesToReview = allDone && task.status === "in_progress";
        if (movesToReview && task.assignedAgentId) {
          noticeAgentId = task.assignedAgentId;
          noticeTitle = task.title;
        }
        return {
          ...task,
          subtasks,
          progress,
          status: movesToReview ? ("pending_review" as const) : task.status,
          completedAt: movesToReview ? nowIso() : task.completedAt,
          artifact: movesToReview
            ? { kind: "text" as const, content: "Checklist complete — output ready for review." }
            : task.artifact,
        };
      });
      return {
        ...state,
        tasks,
        activity: noticeAgentId
          ? withEvent(state.activity, noticeAgentId, `Submitted ${noticeTitle} for review`)
          : state.activity,
        ui: noticeAgentId
          ? { ...state.ui, botNotice: `"${noticeTitle}" is ready for your review.` }
          : state.ui,
      };
    }

    case "approveTask": {
      const task = state.tasks.find((t) => t.id === action.taskId);
      if (!task) return state;
      return {
        ...state,
        tasks: state.tasks.map((t) =>
          t.id === task.id
            ? { ...t, status: "approved", progress: 100, completedAt: nowIso(), feedback: null }
            : t,
        ),
        activity: task.assignedAgentId
          ? withEvent(state.activity, task.assignedAgentId, `Task approved: ${task.title}`)
          : state.activity,
        ui: { ...state.ui, botNotice: `Approved "${task.title}".` },
      };
    }

    case "rejectTask": {
      const task = state.tasks.find((t) => t.id === action.taskId);
      if (!task) return state;
      return {
        ...state,
        tasks: state.tasks.map((t) =>
          t.id === task.id
            ? { ...t, status: "in_progress", flagReason: null, feedback: action.feedback, completedAt: null }
            : t,
        ),
        activity: task.assignedAgentId
          ? withEvent(state.activity, task.assignedAgentId, `Task returned with feedback: ${task.title}`)
          : state.activity,
        ui: { ...state.ui, botNotice: `Sent "${task.title}" back with feedback.` },
      };
    }

    case "inviteAgent": {
      const agent: Agent = {
        id: makeId("agent"),
        name: action.input.name,
        spriteKey: "adam",
        roleTitle: action.input.roleTitle,
        skillTags: action.input.skillTags,
        modelProvider: action.input.modelProvider,
        modelName: action.input.modelName,
        managerAgentId: action.input.managerAgentId,
        status: "online",
        statusSince: nowIso(),
        locationKey: "lobby",
        currentActivity: "Just joined",
        about: action.input.about,
        joinedAt: nowIso(),
      };
      const dm: Channel = { id: makeId("dm"), name: agent.name, kind: "dm", agentId: agent.id };
      const budget: Budget = {
        id: makeId("bud"),
        agentId: agent.id,
        period: "2026-07",
        capTokens: action.input.budgetTokens,
        spentTokens: 0,
      };
      const welcome: ChatMessage = {
        id: makeId("msg"),
        channelId: "ch-general",
        sender: "bot",
        text: `🎉 ${agent.name} just joined as ${agent.roleTitle}. Say hi!`,
        createdAt: nowIso(),
      };
      return {
        ...state,
        agents: [...state.agents, agent],
        agentStats: {
          ...state.agentStats,
          [agent.id]: { tasksCompleted: 0, successRate: 100, focusMinutes: 0 },
        },
        channels: [...state.channels, dm],
        budgets: [...state.budgets, budget],
        messages: [...state.messages, welcome],
        activity: withEvent(state.activity, agent.id, "Joined the company"),
        ui: { ...state.ui, inviteOpen: false, botNotice: `${agent.name} hired! They're in the Lobby.` },
      };
    }

    case "addAnnouncement": {
      const announcement: Announcement = {
        id: makeId("ann"),
        title: action.title,
        body: action.body || null,
        category: action.category,
        createdAt: nowIso(),
      };
      const mirror: ChatMessage = {
        id: makeId("msg"),
        channelId: "ch-announcements",
        sender: "bot",
        text: `📢 ${action.title}${action.body ? ` — ${action.body}` : ""}`,
        createdAt: nowIso(),
      };
      return {
        ...state,
        announcements: [announcement, ...state.announcements],
        messages: [...state.messages, mirror],
      };
    }

    case "setBudgetCap":
      return {
        ...state,
        budgets: state.budgets.map((b) =>
          b.id === action.budgetId ? { ...b, capTokens: action.capTokens } : b,
        ),
      };

    case "exitFocusPod": {
      const agent = state.agents.find((a) => a.id === action.agentId);
      if (!agent) return state;
      return {
        ...state,
        agents: state.agents.map((a) =>
          a.id === agent.id
            ? { ...a, status: "online", statusSince: nowIso(), locationKey: "desk_1", currentActivity: "Back at desk" }
            : a,
        ),
        activity: withEvent(state.activity, agent.id, "Left the Focus Pod"),
        ui: { ...state.ui, activePanel: null, botNotice: `${agent.name} left the Focus Pod.` },
      };
    }

    case "setNotice":
      return { ...state, ui: { ...state.ui, botNotice: action.text } };
    case "dismissNotice":
      return { ...state, ui: { ...state.ui, botNotice: null } };
    case "setInviteOpen":
      return { ...state, ui: { ...state.ui, inviteOpen: action.open } };
    case "setNewTaskOpen":
      return { ...state, ui: { ...state.ui, newTaskOpen: action.open } };

    default:
      return state;
  }
}

interface StoreValue {
  state: AppState;
  dispatch: Dispatch<Action>;
}

const StoreContext = createContext<StoreValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  return <StoreContext.Provider value={{ state, dispatch }}>{children}</StoreContext.Provider>;
}

export function useApp(): StoreValue {
  const value = useContext(StoreContext);
  if (!value) throw new Error("useApp must be used inside AppProvider");
  return value;
}

// Escalation surface = everything waiting on a human (04-api-contract /escalations)
export function escalationCount(tasks: Task[]): number {
  return tasks.filter((t) => t.status === "flagged" || t.status === "pending_review").length;
}
