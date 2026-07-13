import { useApp } from "../shared/store";
import { NavSidebar } from "./NavSidebar";
import { GlobalHeader } from "./GlobalHeader";
import { AppRoutes } from "./routes";
import { LegacyPanelHost } from "./LegacyPanelHost";
import { Toast } from "../ui/Toast";
import { InviteAgentModal } from "../dashboard/panels/InviteAgentModal";
import { NewTaskModal } from "../dashboard/panels/NewTaskModal";
import "./AppShell.css";

export function AppShell() {
  const { state } = useApp();
  const { inviteOpen, newTaskOpen } = state.ui;

  return (
    <div className="app-shell">
      <NavSidebar />

      <div className="app-shell-main-col">
        <GlobalHeader />
        <div className="app-shell-content">
          <AppRoutes />
          <LegacyPanelHost />
        </div>
      </div>

      <Toast />

      {inviteOpen && <InviteAgentModal />}
      {newTaskOpen && <NewTaskModal />}
    </div>
  );
}
