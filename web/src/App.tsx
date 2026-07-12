import { DashboardLayout } from "./dashboard/DashboardLayout";
import { AppProvider } from "./shared/store";

export default function App() {
  return (
    <AppProvider>
      <DashboardLayout />
    </AppProvider>
  );
}
