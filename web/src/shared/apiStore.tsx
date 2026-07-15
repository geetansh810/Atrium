// React-Query-backed provider — the VITE_USE_MOCKS=0 code path. Talks to
// core-api for agents/tasks/budgets (the entities M0.8 wires up); channels,
// messages, announcements and the activity feed stay on static seed data
// because no chat/announcements/analytics endpoints exist yet (04 lists them,
// nothing implements them before M1.x/M2.3) — documented gap, not an oversight.
import { createContext, useContext, useReducer } from "react";
import type { Dispatch, ReactNode } from "react";
import { DEV_COMPANY_ID } from "./config";
import { REAL_ROLE_TEMPLATES } from "./roleTemplateDefaults";
import * as mock from "./mockData";
import {
  describeApiError,
  useApproveTaskMutation,
  useBudgets,
  useCreateTaskMutation,
  useEnrichedTasks,
  useHireAgentMutation,
  usePatchAgentMutation,
  useRejectTaskMutation,
  useRoster,
  useSetBudgetCapMutation,
} from "./queries";
import { escalationCount, initialUiState } from "./storeTypes";
import type { Action, AppState } from "./storeTypes";
import type { ActivityEvent, Announcement, Channel, ChatMessage } from "./types";

export { escalationCount };
export type { Action, AppState };

// The chat/announcements/activity slices have no backend yet — reuse a small
// local reducer over the same seed shape so ChatPanel and the Notifications
// composer keep working unmodified. Agents/tasks/budgets never live here.
interface LocalState {
  channels: Channel[];
  messages: ChatMessage[];
  announcements: Announcement[];
  activity: ActivityEvent[];
  ui: AppState["ui"];
}

const initialLocal: LocalState = {
  channels: mock.channels,
  messages: mock.messages,
  announcements: mock.announcements,
  activity: [],
  ui: initialUiState,
};

type LocalAction = Extract<
  Action,
  {
    type:
      | "setChatOpen"
      | "setChannel"
      | "sendMessage"
      | "addAnnouncement"
      | "setNotice"
      | "dismissNotice"
      | "setInviteOpen"
      | "setNewTaskOpen";
  }
>;

let idCounter = 0;
function makeId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-${Date.now()}-${idCounter}`;
}

function localReducer(state: LocalState, action: LocalAction): LocalState {
  switch (action.type) {
    case "setChatOpen":
      return {
        ...state,
        ui: {
          ...state.ui,
          chatOpen: action.open,
          activeChannelId: action.channelId ?? state.ui.activeChannelId,
        },
      };
    case "setChannel":
      return { ...state, ui: { ...state.ui, activeChannelId: action.channelId } };
    case "sendMessage":
      return {
        ...state,
        messages: [
          ...state.messages,
          { id: makeId("msg"), channelId: action.channelId, sender: action.sender, text: action.text, createdAt: new Date().toISOString() },
        ],
      };
    case "addAnnouncement": {
      const announcement: Announcement = {
        id: makeId("ann"),
        title: action.title,
        body: action.body || null,
        category: action.category,
        createdAt: new Date().toISOString(),
      };
      return {
        ...state,
        announcements: [announcement, ...state.announcements],
        messages: [
          ...state.messages,
          {
            id: makeId("msg"),
            channelId: "ch-announcements",
            sender: "bot",
            text: `📢 ${action.title}${action.body ? ` — ${action.body}` : ""}`,
            createdAt: new Date().toISOString(),
          },
        ],
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

export function ApiAppProvider({ children }: { children: ReactNode }) {
  const companyId = DEV_COMPANY_ID;
  const [local, localDispatch] = useReducer(localReducer, initialLocal);

  const rosterQuery = useRoster(companyId);
  const { tasks } = useEnrichedTasks(companyId);
  const budgetsQuery = useBudgets(companyId);

  const createTaskM = useCreateTaskMutation(companyId);
  const approveTaskM = useApproveTaskMutation(companyId);
  const rejectTaskM = useRejectTaskMutation(companyId);
  const hireAgentM = useHireAgentMutation(companyId);
  const setBudgetCapM = useSetBudgetCapMutation(companyId);
  const patchAgentM = usePatchAgentMutation(companyId);

  const agents = rosterQuery.data ?? [];
  const budgets = budgetsQuery.data ?? [];
  // core-api's AgentController always returns zeroed stats until M2.3's rollups land.
  const agentStats = Object.fromEntries(
    agents.map((a) => [a.id, { tasksCompleted: 0, successRate: 0, focusMinutes: 0 }]),
  );

  const state: AppState = {
    agents,
    agentStats,
    tasks,
    activity: local.activity,
    channels: local.channels,
    messages: local.messages,
    announcements: local.announcements,
    budgets,
    roleTemplates: REAL_ROLE_TEMPLATES,
    ui: local.ui,
  };

  function dispatch(action: Action) {
    switch (action.type) {
      case "setChatOpen":
      case "setChannel":
      case "sendMessage":
      case "addAnnouncement":
      case "setNotice":
      case "dismissNotice":
      case "setInviteOpen":
      case "setNewTaskOpen":
        localDispatch(action);
        return;

      case "toggleSubtask":
        localDispatch({
          type: "setNotice",
          text: "Subtasks are driven by the assigned agent's progress updates — not editable from the dashboard yet.",
        });
        return;

      case "createTask":
        localDispatch({ type: "setNewTaskOpen", open: false });
        createTaskM.mutate(action.input, {
          onSuccess: (detail) =>
            localDispatch({ type: "setNotice", text: `Task "${detail.task.title}" queued.` }),
          onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }),
        });
        return;

      case "approveTask": {
        const task = tasks.find((t) => t.id === action.taskId);
        approveTaskM.mutate(action.taskId, {
          onSuccess: () =>
            localDispatch({ type: "setNotice", text: `Approved "${task?.title ?? action.taskId}".` }),
          onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }),
        });
        return;
      }

      case "rejectTask": {
        const task = tasks.find((t) => t.id === action.taskId);
        rejectTaskM.mutate(
          { taskId: action.taskId, feedback: action.feedback },
          {
            onSuccess: () =>
              localDispatch({ type: "setNotice", text: `Sent "${task?.title ?? action.taskId}" back with feedback.` }),
            onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }),
          },
        );
        return;
      }

      case "inviteAgent":
        localDispatch({ type: "setInviteOpen", open: false });
        hireAgentM.mutate(
          {
            roleTemplateKey: action.input.roleTemplateKey ?? "coder",
            name: action.input.name,
            roleTitle: action.input.roleTitle,
            skillTags: action.input.skillTags,
            modelProvider: action.input.modelProvider,
            modelName: action.input.modelName,
            managerAgentId: action.input.managerAgentId,
            about: action.input.about,
            budgetTokens: action.input.budgetTokens,
          },
          {
            onSuccess: (agent) =>
              localDispatch({ type: "setNotice", text: `${agent.name} hired! They're online.` }),
            onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }),
          },
        );
        return;

      case "setBudgetCap": {
        const budget = budgets.find((b) => b.id === action.budgetId);
        if (!budget) return;
        setBudgetCapM.mutate({ agentId: budget.agentId, period: budget.period, capTokens: action.capTokens });
        return;
      }

      case "exitFocusPod": {
        const agent = agents.find((a) => a.id === action.agentId);
        patchAgentM.mutate(
          { agentId: action.agentId, status: "online" },
          {
            onSuccess: () =>
              localDispatch({
                type: "setNotice",
                text: `${agent?.name ?? "Agent"} left the Focus Pod.`,
              }),
            onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }),
          },
        );
        return;
      }

      case "setAgentPaused": {
        const agent = agents.find((a) => a.id === action.agentId);
        patchAgentM.mutate(
          { agentId: action.agentId, paused: action.paused },
          {
            onSuccess: () =>
              localDispatch({
                type: "setNotice",
                text: `${agent?.name ?? "Agent"} ${action.paused ? "paused" : "resumed"}.`,
              }),
            onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }),
          },
        );
        return;
      }

      default:
        return;
    }
  }

  return <StoreContext.Provider value={{ state, dispatch }}>{children}</StoreContext.Provider>;
}

export function useApiApp(): StoreValue {
  const value = useContext(StoreContext);
  if (!value) throw new Error("useApiApp must be used inside ApiAppProvider");
  return value;
}
