// Dev-time config for the real API (08-conventions.md §Config, dev auth headers
// per 04-api-contract.md). VITE_USE_MOCKS=1 keeps the mock store (mockData.ts);
// unset/0 switches every panel to the real core-api over React Query.
// VITE_COMPANY_ID is written by scripts/seed-dev.mjs after it creates the demo
// company — see web/.env.local (gitignored).

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === "1";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

export const DEV_COMPANY_ID = import.meta.env.VITE_COMPANY_ID ?? "";
