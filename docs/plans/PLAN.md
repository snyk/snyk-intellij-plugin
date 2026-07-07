# snyk-intellij-plugin — Implementation & Roadmap

**Last updated:** 2026-06-29
**Repo:** github.com/snyk/snyk-intellij-plugin

> Single source of truth for completed, in-progress, and pending work.
> Sub-plans linked below carry per-PR specs. Full durable specs live in the engineering brain
> (private — Bastian only).

## Status legend

| Symbol | Meaning |
|--------|---------|
| done | Merged to `master` |
| wip | In progress |
| planned | Planned - not started |
| dropped | Cancelled / deferred |

## Completed work

<!-- add entries here as PRs merge -->

---

## In progress

<!-- active branch work -->

---

## Pending

### Dev-container MockK / ByteBuddy test fix

**Sub-plan:** [container-mockk-bytebuddy-agent.md](container-mockk-bytebuddy-agent.md)

| PR | Title | Items | Notes |
|----|-------|-------|-------|
| wip PR-1 | ByteBuddy preload + JaCoCo backend (unconditional) | `build.gradle.kts`: keep `withType<Test>` agent preload + `useJacoco("0.8.14")` unconditionally in `kover { }` (no flag); simplify `scripts/pre-push-test.sh` (no container detection); update docs, ADR-2 | Makes `./gradlew test` pass everywhere (CI and in-container) with no flags. JaCoCo coexists with MockK's ByteBuddy — no retransform conflict. Local in-container coverage available via `./gradlew koverXmlReport`. Supersedes the `-PdisableKoverInstrumentation` flag approach (ADR-2). |

---

## Architecture reference

| Document | Purpose |
|----------|---------|
| [../../ARCHITECTURE.md](../../ARCHITECTURE.md) | Plugin architecture overview |
