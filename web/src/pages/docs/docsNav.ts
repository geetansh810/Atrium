import type { ComponentType } from "react";
import { Concepts, Overview, Quickstart } from "./content/gettingStarted";
import {
  Architecture,
  Budgets,
  Governance,
  MemoryKnowledge,
  RolesSkills,
  Security,
  TaskLifecycle,
} from "./content/platform";
import { Api, DataModel, Deployment, Modules, TechStack } from "./content/reference";
import { BuildLog, DocumentSet } from "./content/project";

export interface DocHeading {
  id: string;
  label: string;
}

export interface DocPage {
  slug: string;
  title: string;
  description: string;
  Component: ComponentType;
  headings: DocHeading[];
}

export interface DocCategory {
  label: string;
  pages: DocPage[];
}

// The full docs tree. `slug` is the URL segment (the first page's slug is the
// /docs index). `headings` drives the right-rail "On this page" list and must
// match the <Section id> values inside each content component.
export const DOCS: DocCategory[] = [
  {
    label: "Getting started",
    pages: [
      {
        slug: "overview",
        title: "Overview",
        description: "What Atrium is and the three guarantees that hold everywhere.",
        Component: Overview,
        headings: [
          { id: "what", label: "What Atrium is" },
          { id: "principles", label: "The three guarantees" },
          { id: "who", label: "Who it's for" },
          { id: "next", label: "Where to go next" },
        ],
      },
      {
        slug: "concepts",
        title: "Core concepts",
        description: "The vocabulary: companies, agents, roles, tasks, budgets, events.",
        Component: Concepts,
        headings: [
          { id: "company", label: "Company & tenant" },
          { id: "agent", label: "Agent" },
          { id: "role", label: "Role definition" },
          { id: "task", label: "Task" },
          { id: "skill-mem", label: "Skills, memory & knowledge" },
          { id: "budget", label: "Budget & usage" },
          { id: "event", label: "Task event & the outbox" },
        ],
      },
      {
        slug: "quickstart",
        title: "Quickstart",
        description: "Bring the full stack up and assign your first task.",
        Component: Quickstart,
        headings: [
          { id: "run", label: "Run the stack" },
          { id: "keys", label: "Configure a provider" },
          { id: "signup", label: "Create a company" },
          { id: "first-task", label: "Assign real work" },
        ],
      },
    ],
  },
  {
    label: "Platform",
    pages: [
      {
        slug: "architecture",
        title: "Architecture",
        description: "A modular monolith with a transactional-outbox event backbone.",
        Component: Architecture,
        headings: [
          { id: "modules", label: "The nine modules" },
          { id: "outbox", label: "The outbox backbone" },
          { id: "tenancy", label: "Multi-tenancy" },
        ],
      },
      {
        slug: "task-lifecycle",
        title: "Task lifecycle",
        description: "The one path work takes, from request to signed-off output.",
        Component: TaskLifecycle,
        headings: [
          { id: "states", label: "The state machine" },
          { id: "steps", label: "Seven steps" },
          { id: "claim-query", label: "The claim, exactly once" },
          { id: "decomposition", label: "Decomposition" },
        ],
      },
      {
        slug: "roles-skills",
        title: "Roles & skills",
        description: "Capabilities are data you insert, never code you branch on.",
        Component: RolesSkills,
        headings: [
          { id: "roles", label: "Role definitions" },
          { id: "data-not-branches", label: "Roles are data" },
          { id: "skills", label: "Skills" },
        ],
      },
      {
        slug: "memory-knowledge",
        title: "Memory & knowledge",
        description: "The four layers of governed data an agent draws on.",
        Component: MemoryKnowledge,
        headings: [
          { id: "layers", label: "The four layers" },
          { id: "memory", label: "Memory" },
          { id: "knowledge", label: "Knowledge" },
          { id: "learning", label: "Governed learning" },
        ],
      },
      {
        slug: "governance",
        title: "Governance & approvals",
        description: "The review gate, compliance roles, and the completion audit.",
        Component: Governance,
        headings: [
          { id: "gate", label: "The review gate" },
          { id: "compliance", label: "Compliance roles" },
          { id: "audit", label: "The completion audit" },
          { id: "escalations", label: "Escalations" },
        ],
      },
      {
        slug: "budgets",
        title: "Budgets & payroll",
        description: "Token caps, metering, and cost attribution up a delegation chain.",
        Component: Budgets,
        headings: [
          { id: "caps", label: "Caps & scopes" },
          { id: "metering", label: "Metering & idempotency" },
          { id: "payroll", label: "Payroll & attribution" },
        ],
      },
      {
        slug: "security",
        title: "Security & tenancy",
        description: "Two isolation layers, JWT auth, and least-privilege database roles.",
        Component: Security,
        headings: [
          { id: "isolation", label: "Two isolation layers" },
          { id: "auth", label: "Authentication" },
          { id: "db-roles", label: "Database roles" },
          { id: "hardening", label: "Hardening" },
        ],
      },
    ],
  },
  {
    label: "Reference",
    pages: [
      {
        slug: "tech-stack",
        title: "Tech stack",
        description: "Boring infrastructure, carefully used.",
        Component: TechStack,
        headings: [
          { id: "api", label: "Core API" },
          { id: "data", label: "Data" },
          { id: "web", label: "Web" },
          { id: "ops", label: "Operations" },
        ],
      },
      {
        slug: "modules",
        title: "Modules",
        description: "The nine packages and the boundaries between them.",
        Component: Modules,
        headings: [
          { id: "reference", label: "Module reference" },
          { id: "boundaries", label: "Why boundaries matter" },
        ],
      },
      {
        slug: "data-model",
        title: "Data model",
        description: "The core and agent-platform tables and the invariants.",
        Component: DataModel,
        headings: [
          { id: "core", label: "Core tables" },
          { id: "agent-platform", label: "Agent-platform tables" },
          { id: "invariants", label: "Invariants" },
        ],
      },
      {
        slug: "api",
        title: "API",
        description: "The REST surface, scoped to a company by bearer token.",
        Component: Api,
        headings: [
          { id: "auth", label: "Auth" },
          { id: "registry", label: "Registry" },
          { id: "routing", label: "Routing & tasks" },
          { id: "agentmind", label: "Agent mind & accountability" },
        ],
      },
      {
        slug: "deployment",
        title: "Deployment",
        description: "Local compose, the AWS topology, and CI/CD.",
        Component: Deployment,
        headings: [
          { id: "local", label: "Local" },
          { id: "cloud", label: "Cloud architecture" },
          { id: "cicd", label: "CI/CD" },
        ],
      },
    ],
  },
  {
    label: "Project",
    pages: [
      {
        slug: "build-log",
        title: "Build log",
        description: "Five phases, one milestone per session.",
        Component: BuildLog,
        headings: [
          { id: "phases", label: "Five phases" },
          { id: "numbers", label: "By the numbers" },
          { id: "method", label: "How it was built" },
        ],
      },
      {
        slug: "document-set",
        title: "The document set",
        description: "The contracts the implementation answers to.",
        Component: DocumentSet,
        headings: [
          { id: "foundation", label: "Foundation" },
          { id: "platform", label: "Agent platform" },
          { id: "compliance", label: "Compliance" },
        ],
      },
    ],
  },
];

export const DOC_PAGES: DocPage[] = DOCS.flatMap((c) => c.pages);
export const DEFAULT_SLUG = DOC_PAGES[0].slug;

export function findPage(slug: string | undefined): DocPage {
  if (!slug) return DOC_PAGES[0];
  return DOC_PAGES.find((p) => p.slug === slug) ?? DOC_PAGES[0];
}

export function pageNeighbors(slug: string): { prev?: DocPage; next?: DocPage } {
  const i = DOC_PAGES.findIndex((p) => p.slug === slug);
  return { prev: DOC_PAGES[i - 1], next: DOC_PAGES[i + 1] };
}
