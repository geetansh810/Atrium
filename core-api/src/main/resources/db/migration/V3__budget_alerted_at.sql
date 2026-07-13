-- V3 (M0.7): once-per-period dedupe for the budget.threshold soft alert.
-- New row per (company_id, agent_id, period) via the existing UNIQUE constraint,
-- so alerted_at naturally resets when the period rolls over.

ALTER TABLE budgets
  ADD COLUMN alerted_at TIMESTAMPTZ;
