-- M2.5 (15 §0 amended): communication tables — channels, messages, announcements.
-- office_layout (the last piece of the old "V3 communication_office" bundle) is
-- DROPPED: the SkyOffice office track was retired 2026-07-15 (M2.4a) and the Team
-- view derives agent zones from live task state, so there is no desk map to store.
CREATE TABLE channels (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  name TEXT NOT NULL,                          -- 'general','announcements',…
  kind TEXT NOT NULL DEFAULT 'channel' CHECK (kind IN ('channel','dm')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, name)
);

CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL,
  channel_id UUID NOT NULL REFERENCES channels(id),
  sender TEXT NOT NULL,                        -- 'user:<id>'|'agent:<id>'|'bot'
  text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_channel ON messages(channel_id, created_at DESC);

CREATE TABLE announcements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id UUID NOT NULL REFERENCES companies(id),
  title TEXT NOT NULL,
  body TEXT,
  category TEXT NOT NULL DEFAULT 'company',    -- company|update|maintenance
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_announcements_company ON announcements(company_id, created_at DESC);
