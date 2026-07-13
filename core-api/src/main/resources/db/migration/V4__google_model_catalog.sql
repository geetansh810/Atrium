-- V4 — Adds a Google Gemini row to model_catalog (13 §1.2: "adding a model = insert
-- one row, done"). Pairs with the new GoogleClient provider bean (execution/).
--
-- Model choice verified against the LIVE Gemini API on 2026-07-13, not from docs:
-- the 2.5-generation names (gemini-2.5-flash-lite / gemini-2.5-flash) 404 with
-- "no longer available to new users" for keys created now, and the 3.1-pro tier
-- has a free-tier request limit of 0 (paid only). gemini-3.1-flash-lite is the
-- current model that BOTH works and is free-tier eligible on the Gemini Developer
-- API — the whole reason for adding this provider was a genuinely free path.
--
-- Prices are µUSD per MTok (same unit as the Anthropic rows in V2_1); the flash-lite
-- list price is ~$0.10 in / $0.40 out, used here only for usage_records cost
-- accounting — irrelevant while running on the free tier, but the column is NOT NULL.

INSERT INTO model_catalog (provider, model_name, display_name, context_window, max_output_tokens,
                           price_in_micro_usd_per_mtok, price_out_micro_usd_per_mtok, capabilities, tier)
VALUES
('google', 'gemini-3.1-flash-lite', 'Gemini 3.1 Flash-Lite', 1048576, 65536,
   100000, 400000, '["tools","vision","json_mode"]', 'fast');
