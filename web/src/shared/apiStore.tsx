// React-Query-backed provider — the VITE_USE_MOCKS=0 code path. Talks to
// core-api for agents/tasks/budgets AND (M2.5) channels/messages/announcements.
// Only the activity feed stays local (no backend endpoint exists for it yet).
import { createContext, useContext, useReducer } from "react";
import type { Dispatch, ReactNode } from "react";
import { useAuthSession } from "./auth";
import { REAL_ROLE_TEMPLATES } from "./roleTemplateDefaults";
import {
  describeApiError,
  useAddAnnouncementMutation,
  useAgentPerformance,
  useAnnouncements,
  useApproveTaskMutation,
  useBudgets,
  useChannels,
  useChannelMessages,
  useCreateTaskMutation,
  useEnrichedTasks,
  useHireAgentMutation,
  usePatchAgentMutation,
  useRejectTaskMutation,
  useRoster,
  useSendMessageMutation,
  useSetBudgetCapMutation,
} from "./queries";
import { escalationCount, initialUiState } from "./storeTypes";
import type { Action, AppState } from "./storeTypes";
import type { ActivityEvent } from "./types";

export { escalationCount };
export type { Action, AppState };

// Only the activity feed + UI slice live locally now — channels/messages/
// announcements are real API data (M2.5). sendMessage/addAnnouncement dispatch
// straight to mutations below, so they're no longer handled by this reducer.
interface LocalState {
  activity: ActivityEvent[];
  ui: AppState["ui"];
}

const initialLocal: LocalState = {
  activity: [],
  ui: initialUiState,
};

type LocalAction = Extract<
  Action,
  {
    type:
      | "setChatOpen"
      | "setChannel"
      | "setNotice"
      | "dismissNotice"
      | "setInviteOpen"
      | "setNewTaskOpen";
  }
>;

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
  // App.tsx's AuthGate never renders this provider without a session, so this
  // is always a real company by the time any of the hooks below fire.
  const session = useAuthSession();
  const companyId = session?.companyId ?? "";
  const [local, localDispatch] = useReducer(localReducer, initialLocal);

  const rosterQuery = useRoster(companyId);
  const { tasks } = useEnrichedTasks(companyId);
  const budgetsQuery = useBudgets(companyId);
  const channelsQuery = useChannels(companyId);
  const announcementsQuery = useAnnouncements(companyId);

  const createTaskM = useCreateTaskMutation(companyId);
  const approveTaskM = useApproveTaskMutation(companyId);
  const rejectTaskM = useRejectTaskMutation(companyId);
  const hireAgentM = useHireAgentMutation(companyId);
  const setBudgetCapM = useSetBudgetCapMutation(companyId);
  const patchAgentM = usePatchAgentMutation(companyId);
  const sendMessageM = useSendMessageMutation(companyId);
  const addAnnouncementM = useAddAnnouncementMutation(companyId);

  const agents = rosterQuery.data ?? [];
  const budgets = budgetsQuery.data ?? [];
  const channels = channelsQuery.data ?? [];
  const announcements = announcementsQuery.data ?? [];

  // The stored activeChannelId defaults to a mock id ("ch-general") that no real
  // channel matches — fall back to the first real channel so ChatPanel always
  // opens on something valid. Only load messages once we have a real channel id.
  const activeChannelId = channels.some((c) => c.id === local.ui.activeChannelId)
    ? local.ui.activeChannelId
    : channels[0]?.id ?? local.ui.activeChannelId;
  const realActiveId = channels.some((c) => c.id === activeChannelId) ? activeChannelId : undefined;
  const messages = useChannelMessages(companyId, realActiveId).data ?? [];

  // /agents/{id}/profile still returns ProfileStats.zero() (never wired to M2.3's
  // rollups) — the real per-agent numbers live in analytics/agent-performance
  // instead, which M2.3 already built. focusMinutes stays 0: honestly untracked,
  // not stale, matching ReportsPage's M2.3 precedent.
  const performance = useAgentPerformance(companyId).data ?? [];
  const performanceByAgent = new Map(performance.map((p) => [p.agentId, p]));
  const agentStats = Object.fromEntries(
    agents.map((a) => {
      const p = performanceByAgent.get(a.id);
      return [a.id, { tasksCompleted: p?.tasksCompleted ?? 0, successRate: p?.successRate ?? 0, focusMinutes: 0 }];
    }),
  );

  const state: AppState = {
    agents,
    agentStats,
    tasks,
    activity: local.activity,
    channels,
    messages,
    announcements,
    budgets,
    roleTemplates: REAL_ROLE_TEMPLATES,
    ui: { ...local.ui, activeChannelId },
  };

  function dispatch(action: Action) {
    switch (action.type) {
      case "setChatOpen":
      case "setChannel":
      case "setNotice":
      case "dismissNotice":
      case "setInviteOpen":
      case "setNewTaskOpen":
        localDispatch(action);
        return;

      case "sendMessage":
        // Backend assigns the real sender from the (absent) X-User-Id → "user".
        sendMessageM.mutate(
          { channelId: action.channelId, text: action.text },
          { onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }) },
        );
        return;

      case "addAnnouncement":
        addAnnouncementM.mutate(
          { title: action.title, body: action.body, category: action.category },
          {
            onSuccess: () => localDispatch({ type: "setNotice", text: `Announcement "${action.title}" posted.` }),
            onError: (err) => localDispatch({ type: "setNotice", text: describeApiError(err) }),
          },
        );
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
