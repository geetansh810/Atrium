// Real role templates for the API-backed InviteAgentModal, paired 1:1 with the
// 3 global role_definitions seeded by core-api's V2_1__seed.sql (registry M0.2:
// key = 'coder' | 'tester' | 'research'). Real role_definitions carry
// system_prompt/output_contract but not skillTags/model/budget — HireAgentRequest
// (16 §1) still needs those explicitly, so this is the same kind of prefill
// table as the mock roleTemplates.json, just for the 3 templates that actually
// exist server-side, using only Anthropic models (the only live LlmProvider).
import type { RoleTemplate } from "./types";

export const REAL_ROLE_TEMPLATES: RoleTemplate[] = [
  {
    key: "coder",
    title: "Software Engineer",
    description: "Implements, fixes, and refactors code from well-scoped tasks.",
    skillTags: ["Coding", "Code Review", "Testing"],
    modelProvider: "anthropic",
    modelName: "claude-sonnet-5",
    defaultBudgetTokens: 2_500_000,
  },
  {
    key: "tester",
    title: "QA Engineer",
    description: "Writes test cases, reviews code for defects, reproduces bugs.",
    skillTags: ["Testing", "QA"],
    modelProvider: "anthropic",
    modelName: "claude-haiku-4-5",
    defaultBudgetTokens: 1_500_000,
  },
  {
    key: "research",
    title: "Research Specialist",
    description: "Investigates topics, compares options, summarizes with sources.",
    skillTags: ["Web Search", "Analysis", "Summarization"],
    modelProvider: "anthropic",
    modelName: "claude-sonnet-5",
    defaultBudgetTokens: 2_000_000,
  },
];
