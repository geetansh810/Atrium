// Every "open this thing" intent in the app goes through here instead of
// components hand-building URLs or dispatching openPanel directly. Today
// openChat is the only one still opening a legacy overlay panel — that's
// deliberate: as each panel is replaced by a routed page (MF-3 tasks, MF-4
// employees), only this file's implementation changes, not every call site.
import { useNavigate } from "react-router";
import { useApp } from "./store";

export function useAppNav() {
  const { dispatch } = useApp();
  const navigate = useNavigate();

  return {
    openAgent(agentId: string) {
      navigate(`/employees/${agentId}`);
    },
    openTask(taskId: string) {
      navigate(`/tasks/${taskId}`);
    },
    openChat(channelId?: string) {
      dispatch({ type: "openPanel", panel: "chat", channelId });
    },
  };
}
