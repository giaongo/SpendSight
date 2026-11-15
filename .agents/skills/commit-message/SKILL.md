---
name: commit-message
description: Write professional git commit messages for SpendSight and commit staged or changed work as clean, logical commits. Use when the user asks to commit, write or fix a commit message, split changes into commits, or tidy commit history.
allowed-tools: Bash
argument-hint: "[what to commit, optional]"
---

Write commit messages a reviewer can read in a `git log` and understand without opening the diff.

## Steps

1. Look before writing: `git status --short`, then `git diff --stat` and `git diff --staged --stat`. Read the diff of anything you do not already understand. Never describe a change you have not seen.
2. Split into logical commits. One commit = one reason to exist (a feature, a fix, a refactor, docs). Do not mix a feature with unrelated tidying. Stage by path (`git add path ...`), never `git add -A` blindly: leave out `.idea/`, build output, `.env`, `~/`, keys and anything gitignored by intent.
3. Run the relevant checks first when they are cheap: backend `cd backend && .venv/Scripts/python -m pytest -q && .venv/Scripts/ruff check .`; app `cd frontend && ./gradlew :shared:testDebugUnitTest`. Do not commit code you know fails.
4. Write the message (format below) and commit with `git commit -F -` and a heredoc so multi-line text keeps its formatting.
5. Show `git log --oneline -n <count>` afterwards.

## Format

```
Subject line: imperative, capitalised, no full stop, 50 characters if you can, 72 at most

Body: what changed and why, wrapped at 72 columns. Say why the change was
needed or what was wrong, not a line-by-line tour of the diff.

- Use bullets for several distinct points
- Name the user-visible effect first, the mechanism second
```

Subject rules:
- Imperative mood, as if finishing "If applied, this commit will ...": `Add`, `Fix`, `Remove`, `Cap`, `Route`, `Rename`.
- Say what it does, specifically: `Cap invest amount at 10 000 EUR`, not `Fix bug` or `Update code`.
- Plain sentence case. No emoji, no ticket-style prefixes unless the repo already uses them (it does not).
- No filler: never `WIP`, `stuff`, `misc`, `changes`, `final`, `fix again`, `polish` alone.
- Mention the area when it helps: `backend:` or `app:` only if the subject is unclear without it.

Body rules:
- Skip the body only for trivial changes (typo, version bump).
- Explain the reason: the bug, the requirement, the trade-off. If the change is a decision, say what was rejected and why in one line.
- Include figures or behaviour that a reader would otherwise have to run the code to learn (for example "totals now fit the app's Int cents").
- State limits honestly: "English copy only", "not wired into the iOS target".
- No chatty or first-person text ("I tried", "hopefully", "as discussed"). No blame.

## Attribution

End every commit message with the trailer lines the session's attribution reminder asks for (for example `Co-Authored-By: ...` and `Claude-Session: ...`), separated from the body by a blank line. Do not invent your own.

## Safety

- Create new commits; do not amend or rewrite commits that are already pushed unless the user explicitly asks. If they do (for example to reword history), make a backup branch first (`git branch backup/<name>`), confirm the file contents are unchanged with `git diff backup/<name> HEAD`, and ask before any force push (`git push --force-with-lease`, never plain `--force`).
- Never skip hooks (`--no-verify`) or signing.
- Do not push unless the user asked you to.

## Examples

Weak:
```
Polish: let long question chips wrap, fix 'restaurants visits' wording
fix stuff
update invest
```

Professional:
```
Cap invest amount, tighten question matching and hedge the lead

- Cap the monthly amount at 10 000 EUR so totals fit the app's Int cents
- Take a named amount only when the question says it is monthly
- Match only "what if I invest" and "invest that money, surplus, saving"
- State the 5 % example rate in the lead sentence
```

```
Answer in English only and drop language detection

The app is English for now, so the reply language no longer follows the
question. This removes the detector, the script checks and the language
table from verda.py, and every prompt asks for English.
```
