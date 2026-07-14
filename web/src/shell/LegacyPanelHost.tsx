import { useApp } from "../shared/store";
import { AnnouncementsPanel } from "../dashboard/panels/AnnouncementsPanel";
import { ChatPanel } from "../dashboard/panels/ChatPanel";

const PANELS = {
  chat: ChatPanel,
  announcements: AnnouncementsPanel,
} as const;

// Temporary bridge (deleted in MF-6): every panel not yet ported to a routed
// page still renders as a full-viewport overlay, driven by the same
// ui.activePanel/openPanel/closePanel contract as before the redesign. Each
// milestone that ports a panel to a real page removes its entry here.
export function LegacyPanelHost() {
  const { state } = useApp();
  const ActivePanel = state.ui.activePanel ? PANELS[state.ui.activePanel] : null;
  return ActivePanel ? <ActivePanel /> : null;
}
