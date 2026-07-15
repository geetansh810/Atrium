-- M2.3 (15 §0 amended): analytics rollup table only. Channels/messages/
-- announcements/office_layout (the rest of the old "V3 communication_office"
-- bundle) stay for M2.4c/M2.5, which is why this migration is narrowly scoped.
CREATE TABLE agent_stats_daily (
  company_id UUID NOT NULL,
  agent_id UUID NOT NULL REFERENCES agents(id),
  skill TEXT NOT NULL,
  day DATE NOT NULL,
  tasks_completed INT NOT NULL DEFAULT 0,
  tasks_approved INT NOT NULL DEFAULT 0,
  tasks_rejected INT NOT NULL DEFAULT 0,
  focus_minutes INT NOT NULL DEFAULT 0,
  tokens_spent BIGINT NOT NULL DEFAULT 0,
  cost_micro_usd BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (company_id, agent_id, skill, day)
);

CREATE INDEX idx_agent_stats_daily_company_day ON agent_stats_daily(company_id, day);
