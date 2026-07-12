// Typed loader over the JSON fixtures in ./mocks — shapes match shared/types.ts,
// so this file is the only thing to swap for a React Query + real API layer later
// (each export maps 1:1 to an endpoint in atrium-docs/04-api-contract.md).
import type {
  ActivityEvent,
  Agent,
  AgentPerformance,
  AgentStats,
  AnalyticsSummary,
  Announcement,
  Budget,
  Channel,
  ChatMessage,
  CurrentUser,
  DayCount,
  RoleTemplate,
  SkillShare,
  Task,
  TaskCost,
} from "./types";

import activityJson from "./mocks/activity.json";
import agentsJson from "./mocks/agents.json";
import analyticsJson from "./mocks/analytics.json";
import announcementsJson from "./mocks/announcements.json";
import budgetsJson from "./mocks/budgets.json";
import chatJson from "./mocks/chat.json";
import companyJson from "./mocks/company.json";
import roleTemplatesJson from "./mocks/roleTemplates.json";
import tasksJson from "./mocks/tasks.json";

// GET /companies/{id}/roster
export const agents = agentsJson.agents as Agent[];
// part of GET /agents/{id}/profile
export const agentStats = agentsJson.stats as Record<string, AgentStats>;

// GET /companies/{id}/tasks
export const tasks = tasksJson.tasks as unknown as Task[];

// GET /tasks/{id}/events (+ presence), pre-rendered for the activity feed
export const activityEvents = activityJson.events as ActivityEvent[];

// GET /companies/{id}/channels + /channels/{id}/messages
export const channels = chatJson.channels as Channel[];
export const messages = chatJson.messages as ChatMessage[];

// GET /companies/{id}/announcements
export const announcements = announcementsJson.announcements as Announcement[];

// GET /companies/{id}/analytics/*
export const analyticsSummary = analyticsJson.summary as AnalyticsSummary;
export const tasks7d = analyticsJson.tasks7d as DayCount[];
export const agentPerformance = analyticsJson.agentPerformance as AgentPerformance[];
export const topSkills = analyticsJson.topSkills as SkillShare[];

// GET /companies/{id}/budget
export const budgets = budgetsJson.budgets as Budget[];
export const costPerTask = budgetsJson.costPerTask as TaskCost[];

// GET /role-definitions
export const roleTemplates = roleTemplatesJson.templates as RoleTemplate[];

export const currentUser = companyJson.currentUser as CurrentUser;
export const companyName = companyJson.companyName;
