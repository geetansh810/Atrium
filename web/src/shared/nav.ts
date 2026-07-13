// Every "open this thing" intent in the app goes through here instead of
// components hand-building URLs or dispatching openPanel directly. Today most
// of these still open a legacy overlay panel (AgentProfilePanel/WorkspacePanel/
// ChatPanel) — that's deliberate: as each panel is replaced by a routed page
// (MF-3 tasks, MF-4 employees), only this file's implementation changes, not
// every call site.
import { useApp } from "./store";

export function useAppNav() {
  const { dispatch } = useApp();

  return {
    openAgent(agentId: string) {
      dispatch({ type: "openPanel", panel: "profile", agentId });
    },
    openTask(taskId: string) {
      dispatch({ type: "openPanel", panel: "workspace", taskId });
    },
    openChat(channelId?: string) {
      dispatch({ type: "openPanel", panel: "chat", channelId });
    },
  };
}
