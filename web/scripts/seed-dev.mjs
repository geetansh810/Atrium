#!/usr/bin/env node
// Seeds a demo company against a running core-api (docker compose up, or
// core-api on :8080 some other way) and writes the resulting company id into
// web/.env.local so `npm run dev` picks it up as VITE_COMPANY_ID.
//
// Hires the 3 real global role templates (coder/tester/research — the only
// ones that actually exist server-side, all Anthropic models since
// AnthropicClient is the only live LlmProvider) instead of the 6-persona mock
// roster: M0.8's demo company only needs agents that can really claim and run.
//
// Usage: node scripts/seed-dev.mjs [--api-base http://localhost:8080/api/v1]

import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const argApiBase = process.argv.find((a) => a.startsWith("--api-base="))?.split("=")[1];
const API_BASE = argApiBase ?? process.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

async function request(path, { method = "GET", body, companyId } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (companyId) headers["X-Company-Id"] = companyId;
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${method} ${path} -> ${res.status}: ${text}`);
  }
  if (res.status === 204) return undefined;
  return res.json();
}

const AGENTS = [
  {
    roleTemplateKey: "coder",
    name: "CoderAgent",
    roleTitle: "Software Engineer",
    skillTags: ["Coding", "Code Review", "Testing"],
    modelProvider: "anthropic",
    modelName: "claude-sonnet-5",
    about: "Builds and reviews backend and frontend code changes.",
    budgetTokens: 2_500_000,
  },
  {
    roleTemplateKey: "tester",
    name: "TesterAgent",
    roleTitle: "QA Engineer",
    skillTags: ["Testing", "QA"],
    modelProvider: "anthropic",
    modelName: "claude-haiku-4-5",
    about: "Writes tests and reviews code for defects.",
    budgetTokens: 1_500_000,
  },
  {
    roleTemplateKey: "research",
    name: "ResearchAgent",
    roleTitle: "Research Specialist",
    skillTags: ["Web Search", "Analysis", "Summarization"],
    modelProvider: "anthropic",
    modelName: "claude-sonnet-5",
    about: "Investigates topics and summarizes findings with sources.",
    budgetTokens: 2_000_000,
  },
];

async function main() {
  const slug = `atrium-demo-${Date.now().toString(36)}`;
  console.log(`Creating company "Atrium Demo Co" (slug ${slug}) against ${API_BASE}...`);
  const company = await request("/companies", {
    method: "POST",
    body: { name: "Atrium Demo Co", slug },
  });
  console.log(`  company id: ${company.id}`);

  const period = (() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  })();

  // Company-wide cap so the Budget panel's top bar isn't empty.
  await request(`/companies/${company.id}/budget`, {
    method: "PUT",
    companyId: company.id,
    body: { agentId: null, period, capTokens: 10_000_000 },
  });

  for (const spec of AGENTS) {
    const agent = await request(`/companies/${company.id}/agents`, {
      method: "POST",
      companyId: company.id,
      body: {
        name: spec.name,
        roleTemplateKey: spec.roleTemplateKey,
        roleTitle: spec.roleTitle,
        skillTags: spec.skillTags,
        modelProvider: spec.modelProvider,
        modelName: spec.modelName,
        about: spec.about,
      },
    });
    await request(`/companies/${company.id}/budget`, {
      method: "PUT",
      companyId: company.id,
      body: { agentId: agent.id, period, capTokens: spec.budgetTokens },
    });
    // status defaults to 'offline' (V1__core.sql) — flip online so the office
    // isn't empty. Nothing keeps this in sync afterwards: agent.status only
    // moves on an explicit PATCH (no code sets it on claim/complete yet), so
    // "online" here is a one-time visual seed, not a live indicator.
    await request(`/agents/${agent.id}`, {
      method: "PATCH",
      companyId: company.id,
      body: { status: "online" },
    });
    console.log(`  hired ${agent.name} (${agent.id})`);
  }

  const envPath = join(__dirname, "..", ".env.local");
  writeFileSync(
    envPath,
    `# Written by scripts/seed-dev.mjs on ${new Date().toISOString()}\n` +
      `VITE_USE_MOCKS=0\n` +
      `VITE_API_BASE_URL=${API_BASE}\n` +
      `VITE_COMPANY_ID=${company.id}\n`,
  );
  console.log(`\nWrote ${envPath} — restart \`npm run dev\` to pick it up.`);
  console.log(`Company: ${company.name} (${company.id})`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
