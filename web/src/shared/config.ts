// Dev-time config for the real API (08-conventions.md §Config). VITE_USE_MOCKS=1
// keeps the mock store (mockData.ts); unset/0 switches every panel to the real
// core-api over React Query, gated behind the real signup/login flow (M3.1,
// shared/auth.ts) — there's no more dev-header company bootstrap.

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === "1";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
