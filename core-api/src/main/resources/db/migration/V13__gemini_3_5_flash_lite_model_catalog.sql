-- V13 — Adds Gemini 3.5 Flash-Lite to model_catalog (13 §1.2: "adding a model =
-- insert one row, done"). No GoogleClient changes needed — the provider is
-- fully catalog-driven, model name is just a request parameter.
--
-- Verified against the LIVE Gemini API on 2026-07-23 (same "list-and-probe the
-- real key" practice V4's own comment documents): `gemini-3.5-flash-lite` is a
-- real, GA, callable model — confirmed via GET /v1beta/models and a live
-- generateContent call using this environment's GOOGLE_API_KEY. Knowledge
-- cutoff March 2026 per the model card.
--
-- Prices are µUSD per MTok (same unit as every other row): standard-tier list
-- price is $0.30 in / $2.50 out per ai.google.dev/gemini-api/docs/pricing.

INSERT INTO model_catalog (provider, model_name, display_name, context_window, max_output_tokens,
                           price_in_micro_usd_per_mtok, price_out_micro_usd_per_mtok, capabilities, tier)
VALUES
('google', 'gemini-3.5-flash-lite', 'Gemini 3.5 Flash-Lite', 1048576, 65536,
   300000, 2500000, '["tools","vision","json_mode"]', 'fast');
