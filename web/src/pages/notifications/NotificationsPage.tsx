import { useApp } from "../../shared/store";
import { PagePlaceholder } from "../PagePlaceholder";

export function NotificationsPage() {
  const { dispatch } = useApp();
  return (
    <PagePlaceholder
      title="Notifications"
      subtitle="Derived feed arrives in MF-6"
      description="Until then, the classic Announcements panel covers company-wide notices."
      actions={[
        { label: "Open Announcements", onClick: () => dispatch({ type: "openPanel", panel: "announcements" }) },
      ]}
    />
  );
}
