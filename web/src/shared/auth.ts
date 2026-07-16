// Session storage for the real API (M3.1) — replaces the old dev-header
// bootstrap (VITE_COMPANY_ID) entirely. Mock mode never touches this file.
import { createDomainStore } from "./domains/createDomainStore";

export interface AuthSession {
  token: string;
  companyId: string;
  companyName: string;
  companySlug: string;
  userId: string;
  displayName: string;
  role: string;
}

const STORAGE_KEY = "atrium:auth";

function loadStoredSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuthSession) : null;
  } catch {
    return null;
  }
}

const authStore = createDomainStore<AuthSession | null>(loadStoredSession());

/** Reactive — components re-render on login/logout. */
export const useAuthSession = authStore.useDomainState;

/** Non-reactive — for one-off reads outside components (e.g. api.ts's fetch wrapper). */
export function getAuthSession(): AuthSession | null {
  return authStore.get();
}

export function setAuthSession(session: AuthSession) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  authStore.set(session);
}

export function clearAuthSession() {
  localStorage.removeItem(STORAGE_KEY);
  authStore.set(null);
}
