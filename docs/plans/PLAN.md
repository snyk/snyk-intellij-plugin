# snyk-intellij-plugin — Implementation & Roadmap

**Last updated:** 2026-06-19
**Repo:** github.com/snyk/snyk-intellij-plugin

> Single source of truth for completed, in-progress, and pending work.
> Sub-plans carry per-PR TDD specs. NOTE: in this repo, sub-plan files named `IDE-*.md` match
> `.gitignore:49` (`IDE-*`) and cannot be committed — they live locally and in the engineering
> brain. This PLAN.md is committed and is the team-visible roadmap.

## Status legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Merged to `master` |
| 🔄 | In progress |
| ⏳ | Planned — not started |
| ❌ | Cancelled / deferred |

## Completed work

<!-- add entries here as PRs merge -->

---

## In progress

<!-- active branch work -->

---

## Pending

### IDE-1954 — Automatically Refresh HTML Settings page

**Sub-plan:** `IDE-1954-auto-refresh-html-settings.md` (local + brain only — gitignored in this repo)

When the Language Server emits `$/snyk.configuration` (now fired on auth, org change, folder
add/remove per merged snyk-ls PR #1345) and the HTML Settings page is open, the page auto-refreshes
its configuration without the user closing and reopening it. IntelliJ already handles the
notification; this story adds the missing panel-reload reaction.

| PR | Title | Items | Notes |
|----|-------|-------|-------|
| ⏳ PR-1 | feat(settings): auto-refresh HTML settings page on LS configuration change | Public `HTMLSettingsPanel.reloadFromLanguageServer()`; call it from `SnykLanguageClient.snykConfiguration`; INT-001/002/003 + UNIT-001 | Single PR, < 700 lines |

---

## Architecture reference

| Document | Purpose |
|----------|---------|
| `IDE-1954-auto-refresh-html-settings.md` | IDE-1954 TDD spec (local/brain) |
