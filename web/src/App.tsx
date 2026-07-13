import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DashboardLayout } from "./dashboard/DashboardLayout";
import { AppProvider } from "./shared/store";

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AppProvider>
        <DashboardLayout />
      </AppProvider>
    </QueryClientProvider>
  );
}
