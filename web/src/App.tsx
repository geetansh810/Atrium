import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router";
import { AppShell } from "./shell/AppShell";
import { AppProvider } from "./shared/store";
import { AuthPage } from "./pages/auth/AuthPage";
import { useAuthSession } from "./shared/auth";
import { USE_MOCKS } from "./shared/config";

const queryClient = new QueryClient();

// M3.1: mock mode never needed auth (it's all local fixtures) — the real API
// gates the whole dashboard behind a signed-in session, checked once here
// rather than per-route, since there's nothing to show without a companyId.
function AuthGate() {
  const session = useAuthSession();
  if (!session) return <AuthPage />;
  return (
    <AppProvider>
      <AppShell />
    </AppProvider>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {USE_MOCKS ? (
          <AppProvider>
            <AppShell />
          </AppProvider>
        ) : (
          <AuthGate />
        )}
      </BrowserRouter>
    </QueryClientProvider>
  );
}
