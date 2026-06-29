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
| planned PR-1 | ByteBuddy preload + Kover-instrumentation opt-out for the test task | `build.gradle.kts`: keep `withType<Test>` agent preload + add default-OFF `-PdisableKoverInstrumentation` guard in `kover { }` + verify byte-buddy force; docs + pre-push hook | Makes `./gradlew test -PdisableKoverInstrumentation` pass in the LinuxKit dev-container (fixes the Kover-MockK retransform conflict); CI + coverage unchanged (flag default-OFF). Supersedes the `SKIP=test` workaround. FOLLOW-UP: restore local in-container coverage. |

---

## Architecture reference

| Document | Purpose |
|----------|---------|
| [../../ARCHITECTURE.md](../../ARCHITECTURE.md) | Plugin architecture overview |
