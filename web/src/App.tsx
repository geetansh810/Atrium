import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router";
import { AppShell } from "./shell/AppShell";
import { AppProvider } from "./shared/store";
import { AuthPage } from "./pages/auth/AuthPage";
import { OnboardingWizard } from "./pages/onboarding/OnboardingWizard";
import { useAuthSession } from "./shared/auth";
import { completeOnboarding, needsOnboarding } from "./shared/onboarding";
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
      <OnboardingOrShell companyId={session.companyId} />
    </AppProvider>
  );
}

// M3.4: a fresh signup owes the onboarding wizard (shared/onboarding.ts's
// localStorage flag, set by AuthPage) before it ever sees AppShell. Checked
// once at mount, not derived from the live roster — the roster query is still
// loading (empty) on first render for an already-onboarded company too, and
// that must never flash the wizard.
function OnboardingOrShell({ companyId }: { companyId: string }) {
  const [pending, setPending] = useState(() => needsOnboarding(companyId));
  if (pending) {
    return (
      <OnboardingWizard
        companyId={companyId}
        onDone={() => {
          completeOnboarding(companyId);
          setPending(false);
        }}
      />
    );
  }
  return <AppShell />;
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
