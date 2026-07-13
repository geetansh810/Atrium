import { useApp } from "../../shared/store";
import { PagePlaceholder } from "../PagePlaceholder";

export function ReportsPage() {
  const { dispatch } = useApp();
  return (
    <PagePlaceholder
      title="Reports"
      subtitle="Arriving in MF-5"
      description="Until then, the classic Analytics panel covers agent performance and task throughput."
      actions={[{ label: "Open Analytics", onClick: () => dispatch({ type: "openPanel", panel: "analytics" }) }]}
    />
  );
}
