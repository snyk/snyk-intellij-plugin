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

### Dev-container local code coverage (JaCoCo) — follow-up

**Sub-plan:** [container-local-coverage-jacoco.md](container-local-coverage-jacoco.md)

| PR | Title | Items | Notes |
|----|-------|-------|-------|
| planned PR-1 | Measure in-container coverage with JaCoCo so MockK tests run with coverage | `build.gradle.kts` `kover { }`: add default-OFF `-PkoverUseJacoco` → `useJacoco("0.8.14")` guard; docs + pre-push hook + brain | Restores the local coverage that `-PdisableKoverInstrumentation` removed. JaCoCo's coverage agent coexists with MockK's inline retransform where native Kover does not (verified). CI unchanged (flag default-OFF → native Kover; `mi-kas/kover-report` gate untouched). Branch from `master` after the MockK/ByteBuddy fix merges. Residual: independent IntelliJ `VFS can't be initialized` failures (~131/185 tests) cap which tests contribute coverage — separate follow-up. |

---

## Architecture reference

| Document | Purpose |
|----------|---------|
| [../../ARCHITECTURE.md](../../ARCHITECTURE.md) | Plugin architecture overview |
