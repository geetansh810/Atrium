-- V5 — Seed data (M-SK1): 2-3 platform skills per seeded role template
-- (coder/tester/research, global company_id NULL), attached to those
-- templates via role_definition_skills so hiring auto-attaches them.
--
-- No DDL needed here: V2__agent_platform.sql (M0.1) already created the full
-- Rev C schema verbatim, incl. skills/role_definition_skills/agent_skills —
-- M0.1 pasted 15 §§2-4 as a whole, ahead of the milestones that build the
-- Java/API layers on top of each table. This migration is seed data only.

-- ── Skills ───────────────────────────────────────────────────────────────

INSERT INTO skills (company_id, key, version, name, description, body_md, kind, tags, trust_level, source, created_by)
VALUES
(NULL, 'code-review-checklist', 1, 'Code review checklist', 'What to check before calling a diff done',
'## Code review checklist

Before submitting any code artifact, verify:

1. **Correctness** — does it do exactly what the task asked, nothing more, nothing less?
2. **Edge cases** — empty input, null/None, zero, negative numbers, boundary indices.
3. **Naming** — names say what a thing is; no abbreviations that need a comment to explain.
4. **No dead code** — no commented-out blocks, no unused imports or variables.
5. **Error handling matches the codebase''s existing idiom** — do not invent a new error-handling style for one function.
6. **Tests, if requested** — cover the happy path plus at least one edge case from #2.

If any item fails, fix it before delivering — do not note it as a TODO and ship anyway.',
'procedure', ARRAY['coding','review'], 'platform', 'template', 'system'),

(NULL, 'unified-diff-output', 1, 'Output format: unified diff', 'How to present code changes against existing context',
'## Output format: unified diff

When the task provides existing file context, present your change as a unified diff (`--- a/`, `+++ b/`, `@@` hunks) rather than restating the whole file. When no context was given (new file), give full file contents in a single fenced code block with the filename as the block''s first-line comment.

Never mix prose explanation into the code block. Explanation goes in a separate "Notes" section after the diff, per the coder role''s output contract.',
'reference', ARRAY['coding','format'], 'platform', 'template', 'system'),

(NULL, 'test-coverage-checklist', 1, 'Test coverage checklist', 'What a complete test suite for a task must cover',
'## Test coverage checklist

For every function or feature under test, write cases covering:

1. **Happy path** — the documented, expected use.
2. **Boundaries** — empty collections, zero, min/max values, off-by-one indices.
3. **Invalid input** — wrong type, null/None, malformed data — assert the documented failure behavior, not a guess.
4. **Concurrency/ordering**, only if the code under test is actually concurrent — flag (don''t silently skip) if you can''t safely test it.

Each test name or comment must state what it verifies in one sentence. A test whose purpose can''t be stated in one sentence is testing the wrong thing.',
'procedure', ARRAY['testing','qa'], 'platform', 'template', 'system'),

(NULL, 'bug-report-contract', 1, 'Bug report contract', 'Required shape for a reported defect',
'## Bug report contract

Every bug report must contain, in this order:

1. **Steps to reproduce** — numbered, minimal, deterministic.
2. **Expected behavior.**
3. **Actual behavior** — include the literal error message or wrong output, not a paraphrase.
4. **Severity** — one of blocker/major/minor/cosmetic, with a one-line justification.

Never report a style preference as a bug. If reproduction is flaky, say so explicitly rather than presenting it as deterministic.',
'policy', ARRAY['testing','qa'], 'platform', 'template', 'system'),

(NULL, 'source-ranking-procedure', 1, 'Source-ranking procedure', 'How to weigh conflicting sources during research',
'## Source-ranking procedure

Rank sources, highest trust first:

1. Primary documentation (official docs, source code, standards bodies).
2. Reputable secondary sources (established publications, maintainer blog posts).
3. Community sources (forums, Q&A sites, unverified blogs) — usable for leads, never as the sole citation for a factual claim.

When sources conflict, state both positions, name which you weighted higher, and why (recency, authority, corroboration count). Never silently pick one and hide the disagreement.',
'procedure', ARRAY['research'], 'platform', 'template', 'system'),

(NULL, 'summarization-contract', 1, 'Summarization contract', 'Rules for compressing source material into a report',
'## Summarization contract

- Summaries must be substantially shorter than their source and written in your own words — never a lightly reworded copy.
- Every factual claim carries a source reference.
- Label inference separately from fact ("Sources do not state this directly, but X suggests...").
- State explicitly what you could not verify or what is likely outdated.',
'policy', ARRAY['research','writing'], 'platform', 'template', 'system');

-- ── Attach to role templates (position = display order) ────────────────────

INSERT INTO role_definition_skills (role_definition_id, skill_id, position)
SELECT rd.id, s.id, v.position
FROM (VALUES
  ('coder',    'code-review-checklist',     0),
  ('coder',    'unified-diff-output',       1),
  ('tester',   'test-coverage-checklist',   0),
  ('tester',   'bug-report-contract',       1),
  ('research', 'source-ranking-procedure',  0),
  ('research', 'summarization-contract',    1)
) AS v(role_key, skill_key, position)
JOIN role_definitions rd ON rd.company_id IS NULL AND rd.key = v.role_key AND rd.version = 1
JOIN skills s ON s.company_id IS NULL AND s.key = v.skill_key AND s.version = 1;
