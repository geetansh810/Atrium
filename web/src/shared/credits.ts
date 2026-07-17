// M3.5: mirrors THIRD-PARTY-LICENSES.md's "Frontend" table at the repo root —
// keep the two in sync by hand if this list changes (only `dependencies` in
// package.json ship to the browser; `devDependencies` like TypeScript/Vite/
// oxlint are build-time tooling only and never reach a user).
export interface CreditEntry {
  name: string;
  license: string;
}

export const FRONTEND_CREDITS: CreditEntry[] = [
  { name: "React / React DOM", license: "MIT" },
  { name: "react-router", license: "MIT" },
  { name: "@tanstack/react-query", license: "MIT" },
];
