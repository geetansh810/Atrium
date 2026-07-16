// M3.4: starter roster packs for the post-signup onboarding wizard. Each
// member hires from a real global role_definitions template (core-api's
// V9__seed_starter_roster_templates.sql adds 'lead'/'product'/'content'
// alongside M0.2's 'coder'/'tester'/'research') — "template packs... as data"
// per 11-session-prompts.md's M3.4 card, so adding a pack is just a new
// array entry, never new wizard/router code.
import type { ModelProvider } from "./types";

export interface StarterPackMember {
  roleTemplateKey: string;
  name: string;
  roleTitle: string;
  skillTags: string[];
  modelProvider: ModelProvider;
  modelName: string;
  budgetTokens: number;
}

export interface StarterPack {
  key: string;
  name: string;
  description: string;
  members: StarterPackMember[];
  sampleTask: { title: string; description: string; skillTag: string };
}

export const STARTER_PACKS: StarterPack[] = [
  {
    key: "engineering-pod",
    name: "Engineering pod",
    description: "A lead plus two coders and a tester — ready to scope, build, and check a feature end to end.",
    members: [
      {
        roleTemplateKey: "lead",
        name: "Alex",
        roleTitle: "Engineering Lead",
        skillTags: ["Planning"],
        modelProvider: "anthropic",
        modelName: "claude-sonnet-5",
        budgetTokens: 2_000_000,
      },
      {
        roleTemplateKey: "coder",
        name: "Sam",
        roleTitle: "Software Engineer",
        skillTags: ["Coding"],
        modelProvider: "anthropic",
        modelName: "claude-sonnet-5",
        budgetTokens: 2_500_000,
      },
      {
        roleTemplateKey: "coder",
        name: "Jordan",
        roleTitle: "Software Engineer",
        skillTags: ["Coding"],
        modelProvider: "anthropic",
        modelName: "claude-sonnet-5",
        budgetTokens: 2_500_000,
      },
      {
        roleTemplateKey: "tester",
        name: "Taylor",
        roleTitle: "QA Engineer",
        skillTags: ["Testing"],
        modelProvider: "anthropic",
        modelName: "claude-haiku-4-5",
        budgetTokens: 1_500_000,
      },
    ],
    sampleTask: {
      title: "Write a function to reverse a string",
      description:
        "A small warm-up task — enough to watch a real agent claim it, do the work, and hand it back for your review.",
      skillTag: "Coding",
    },
  },
  {
    key: "content-team",
    name: "Content team",
    description: "A product manager plus two content writers — ready to plan and draft your first campaign.",
    members: [
      {
        roleTemplateKey: "product",
        name: "Riley",
        roleTitle: "Product Manager",
        skillTags: ["Planning"],
        modelProvider: "anthropic",
        modelName: "claude-sonnet-5",
        budgetTokens: 2_000_000,
      },
      {
        roleTemplateKey: "content",
        name: "Morgan",
        roleTitle: "Content Writer",
        skillTags: ["Content"],
        modelProvider: "anthropic",
        modelName: "claude-sonnet-5",
        budgetTokens: 2_000_000,
      },
      {
        roleTemplateKey: "content",
        name: "Casey",
        roleTitle: "Content Writer",
        skillTags: ["Content"],
        modelProvider: "anthropic",
        modelName: "claude-sonnet-5",
        budgetTokens: 2_000_000,
      },
    ],
    sampleTask: {
      title: "Draft a welcome email for new customers",
      description:
        "A small warm-up task — enough to watch a real agent claim it, do the work, and hand it back for your review.",
      skillTag: "Content",
    },
  },
];
