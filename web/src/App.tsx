import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router";
import { AppShell } from "./shell/AppShell";
import { AppProvider } from "./shared/store";
import { LandingPage } from "./pages/landing/LandingPage";
import { DocsSite } from "./pages/docs/DocsSite";
import { AuthPage } from "./pages/auth/AuthPage";
import { OnboardingWizard } from "./pages/onboarding/OnboardingWizard";
import { useAuthSession } from "./shared/auth";
import { completeOnboarding, needsOnboarding } from "./shared/onboarding";
import { USE_MOCKS } from "./shared/config";

const queryClient = new QueryClient();

// M-LP1: the signed-out entry point — a marketing page at "/" with real
// /signup and /login routes into AuthPage, replacing the old bare AuthPage-at-"/"
// default. Any unknown path while signed out falls back to the landing page
// rather than a 404, since there's no dashboard state to deep-link into yet.
function PublicApp() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/signup" element={<AuthPage initialMode="signup" />} />
      <Route path="/login" element={<AuthPage initialMode="login" />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

// M3.1: mock mode never needed auth (it's all local fixtures) — the real API
// gates the whole dashboard behind a signed-in session, checked once here
// rather than per-route, since there's nothing to show without a companyId.
function AuthGate() {
  const session = useAuthSession();
  if (!session) return <PublicApp />;
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

// Docs is a public surface reachable whether or not a visitor is signed in, so
// it's branched here at the root — above the auth gate — rather than living
// inside either router. It renders its own <Routes> for the /docs subtree.
// Everything else keeps its existing top-level routing untouched.
function Root() {
  const { pathname } = useLocation();
  if (pathname === "/docs" || pathname.startsWith("/docs/")) return <DocsSite />;
  if (USE_MOCKS) {
    return (
      <AppProvider>
        <AppShell />
      </AppProvider>
    );
  }
  return <AuthGate />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Root />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
