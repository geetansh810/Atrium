// Provider swap point (M0.8): VITE_USE_MOCKS picks MockAppProvider
// (shared/mockStore.tsx, the original reducer over JSON fixtures) or
// ApiAppProvider (shared/apiStore.tsx, React Query over core-api). The choice
// is made once at module load — not a per-render branch — so components never
// see a hook-count mismatch between the two implementations.
import { USE_MOCKS } from "./config";
import { MockAppProvider, useMockApp } from "./mockStore";
import { ApiAppProvider, useApiApp } from "./apiStore";

export const AppProvider = USE_MOCKS ? MockAppProvider : ApiAppProvider;
export const useApp = USE_MOCKS ? useMockApp : useApiApp;

export { escalationCount } from "./storeTypes";
export type { Action, AppState, InviteAgentInput, NewTaskInput } from "./storeTypes";
