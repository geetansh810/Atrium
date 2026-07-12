# 09 — LLM-Assisted Development Workflow

How to run each build session so any LLM produces code that connects correctly.

## Session template (copy-paste skeleton)

```
[1. PRIMING BLOCK — from 00-MASTER-PLAN.md §1, always]

[2. MILESTONE CARD — the one card from 07-milestones.md, verbatim]

[3. CONTRACTS — only the relevant excerpts:]
   - Tables this milestone touches (from 03)
   - Endpoints this milestone implements/consumes (from 04)
   - The module spec for the module you're in (from 05)

[4. CURRENT STATE — paste:]
   - Output of `tree -L 3` for the affected service
   - The 1–3 existing files most relevant (e.g. the service interface being implemented against)

[5. INSTRUCTION:]
"Implement milestone <ID> exactly as specified. Follow the conventions in the priming
block. Do not modify files outside <module/paths>. Do not change the contract —
if the contract seems wrong, stop and tell me instead of working around it.
Produce: (a) list of files created/changed, (b) the code, (c) how to run the
Done-when verification, (d) anything you were uncertain about."
```

## Rules of engagement
1. **One milestone per session.** If a session wants to touch another module: stop, that's a contract bug.
2. **Never let the LLM invent contract details.** Missing from 03/04? Decide it yourself, update the doc, then continue.
3. **Fresh session per milestone** — don't drag a 50-message context; the docs replace memory.
4. **For fork work (M2.4a–c):** also paste `docs/notes/skyoffice-findings.md` and the relevant upstream file(s) being modified.
5. **UI milestones:** attach the reference image(s) from assets/ to the session; instruct "match the structure and information hierarchy of the reference, using theme.ts tokens; do not invent extra panels."

## Review checklist (before merging any LLM-written diff)
- [ ] Done-when verified by running it, personally.
- [ ] Every new query is company-scoped. Grep for repository methods without company_id.
- [ ] task_events appended inside the same transaction as any status change.
- [ ] No secrets in code, logs, or prompt assembly.
- [ ] No `if (skill == …)` / role-specific branches in routing.
- [ ] No new dependency without a reason written in the PR description.
- [ ] Migrations are new files, never edits to applied ones.
- [ ] Diff read in full — anything not understood gets explained or removed.

## When the LLM gets stuck or produces junk
- Reduce scope: split the milestone card in half, do the data layer first.
- Paste the failing test output verbatim — one error at a time.
- If it keeps "fixing" by weakening an invariant (e.g. dropping the tenant filter): that's a hard stop; do that part by hand.

## Session log
Keep `docs/notes/session-log.md`: one line per session — date, milestone, result (done/partial/redo), lesson. This is what keeps a months-long AI-assisted build coherent.
