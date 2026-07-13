// Every "open this thing" intent in the app goes through here instead of
// components hand-building URLs or dispatching openPanel directly. Today some
// of these still open a legacy overlay panel (AgentProfilePanel/ChatPanel) —
// that's deliberate: as each panel is replaced by a routed page (MF-3 tasks
// done, MF-4 employees next), only this file's implementation changes, not
// every call site.
import { useNavigate } from "react-router";
import { useApp } from "./store";

export function useAppNav() {
  const { dispatch } = useApp();
  const navigate = useNavigate();

  return {
    openAgent(agentId: string) {
      dispatch({ type: "openPanel", panel: "profile", agentId });
    },
    openTask(taskId: string) {
      navigate(`/tasks/${taskId}`);
    },
    openChat(channelId?: string) {
      dispatch({ type: "openPanel", panel: "chat", channelId });
    },
  };
}
