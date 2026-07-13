// The original mock-data store (pre-M0.8): a plain reducer over the JSON
// fixtures. Kept verbatim as the VITE_USE_MOCKS=1 code path — apiStore.tsx is
// the React Query equivalent that talks to core-api.
import { createContext, useContext, useReducer } from "react";
import type { Dispatch, ReactNode } from "react";
import * as mock from "./mockData";
import {
  escalationCount,
  initialUiState,
  makeId,
  nowIso,
} from "./storeTypes";
import type { Action, AppState } from "./storeTypes";
import type {
  ActivityEvent,
  Agent,
  Announcement,
  Budget,
  Channel,
  ChatMessage,
  Task,
} from "./types";

export { escalationCount };
export type { Action, AppState };
export type { PanelKey, NewTaskInput, InviteAgentInput } from "./storeTypes";

const initialState: AppState = {
  agents: mock.agents,
  agentStats: mock.agentStats,
  tasks: mock.tasks,
  activity: mock.activityEvents,
  channels: mock.channels,
  messages: mock.messages,
  announcements: mock.announcements,
  budgets: mock.budgets,
  roleTemplates: mock.roleTemplates,
  ui: initialUiState,
};

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

export function MockAppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  return <StoreContext.Provider value={{ state, dispatch }}>{children}</StoreContext.Provider>;
}

export function useMockApp(): StoreValue {
  const value = useContext(StoreContext);
  if (!value) throw new Error("useMockApp must be used inside MockAppProvider");
  return value;
}
