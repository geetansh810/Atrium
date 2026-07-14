import { useApp } from "../shared/store";
import { NavSidebar } from "./NavSidebar";
import { GlobalHeader } from "./GlobalHeader";
import { AppRoutes } from "./routes";
import { Toast } from "../ui/Toast";
import { CommandPalette } from "../ui/CommandPalette";
import { ChatPanel } from "../dashboard/panels/ChatPanel";
import { InviteAgentModal } from "../dashboard/panels/InviteAgentModal";
import { NewTaskModal } from "../dashboard/panels/NewTaskModal";
import "./AppShell.css";

export function AppShell() {
  const { state } = useApp();
  const { inviteOpen, newTaskOpen, chatOpen } = state.ui;

  return (
    <div className="app-shell">
      <NavSidebar />

      <div className="app-shell-main-col">
        <GlobalHeader />
        <div className="app-shell-content">
          <AppRoutes />
        </div>
      </div>

      <Toast />
      <CommandPalette />

      {chatOpen && <ChatPanel />}
      {inviteOpen && <InviteAgentModal />}
      {newTaskOpen && <NewTaskModal />}
    </div>
  );
}
