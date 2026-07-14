// Every "open this thing" intent in the app goes through here instead of
// components hand-building URLs or dispatching store actions directly.
// openAgent/openTask are real routes; openChat toggles the global chat
// drawer (AppShell-level, MF-6 — the last of the three to graduate off the
// old panel-dispatch pattern).
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
      dispatch({ type: "setChatOpen", open: true, channelId });
    },
  };
}
