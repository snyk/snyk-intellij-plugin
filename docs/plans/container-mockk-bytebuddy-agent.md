<!-- sub-plan: linked from docs/plans/PLAN.md -->

# ByteBuddy preload + JaCoCo backend (unconditional) so MockK tests run in the dev container

**Ticket:** none assigned
**Branch:** `fix/container-mockk-bytebuddy-agent` (from `master`)
**Status:** In progress — JaCoCo-everywhere approach (ADR-2; supersedes flag-gated ADR-1)
**Last updated:** 2026-06-29

> Full durable spec (test scenarios, risks, decision log) and ADR-1 live in the engineering brain.
> This file is the committed, repo-visible per-PR summary linked from `docs/plans/PLAN.md`.

## Problem (customer outcome)

A developer in the Docker Desktop dev-container cannot run `./gradlew test` locally: MockK-based
tests fail at initialization, forcing `SKIP=test` on push. CI runs the same suite green because it
runs on GitHub-hosted runner VMs, not the dev-container's LinuxKit VM. This change makes the
in-container suite initialize and pass **without changing CI behaviour or coverage**.

## Root cause (validated) — two independent failures

1. **Attach (fixed by the agent preload, already on the branch):** in the LinuxKit VM, HotSpot dynamic
   attach is non-functional (the attach-listener socket is never created). MockK uses a runtime JVM
   self-attach (via ByteBuddy) at init to get a retransformation-capable `Instrumentation` handle, so
   MockK init throws `AttachNotSupportedException`. Fixed by preloading the ByteBuddy agent.
2. **Retransform (this change):** the residual failures are
   `UnsupportedOperationException: class redefinition failed: attempted to delete a method` on
   `mockkStatic` / final-class retransforms. **Root cause: the Kover coverage java-agent conflicts with
   MockK's ByteBuddy inline instrumentation in the same test JVM.** Proven: disabling Kover
   instrumentation in-container took `UtilsKtTest` from 12/16 failing to 1/16 (the remaining one an
   ordinary assertion), and a `mockkStatic` test built successfully — on the same bundled JBR.

It is **NOT** caused by JBR, **NOT** by Linux/ARM, and **NOT** by the byte-buddy version. A runtime
switch off JBR is **impossible** (the IntelliJ Platform Gradle Plugin pins the bundled JBR for the
test task; a Corretto launcher override is silently ignored). Disproven dead ends — do NOT add:
`java.io.tmpdir` changes, `-Djdk.attach.allowAttachSelf=true`, `-XX:+StartAttachListener`,
`SYS_PTRACE`, a non-JBR runtime, or treating byte-buddy 1.15.x vs 1.14.17 as the cause.

## Fix

**Part A — ByteBuddy preload (already on the branch; keep).** In `build.gradle.kts`
`tasks { withType<Test> { ... } }`, preload the ByteBuddy agent via `jvmArgumentProviders` (a
`CommandLineArgumentProvider`), sourcing `byte-buddy-agent-*.jar` **dynamically by filename** from the
resolved test classpath (transitive via `io.mockk:mockk-agent-jvm` -> `net.bytebuddy:byte-buddy-agent`;
version unpinned, currently 1.15.11). Match the trailing hyphen so the core `byte-buddy-<ver>.jar` is
never selected; fail the build loudly on a !=1 match. Unconditional on all platforms (preload is a
harmless superset of runtime attach, so CI stays identical); `withType<Test>` covers extra test tasks.

**Part B — JaCoCo backend unconditional (supersedes flag-gated opt-out, ADR-2).** In the existing
`kover { }` block, replace the conditional `disableKoverInstrumentation` guard with an unconditional
`useJacoco("0.8.14")` as the first statement. JaCoCo instruments at class-load time (not
retransformation), so it does not conflict with MockK's ByteBuddy inline instrumentation.
Validated: JaCoCo overall coverage = 52.03% on CI (vs native Kover 54.26%), well above the 40%
gate. CI and in-container now use the same backend — no flags, no container detection.

```kotlin
kover {
  useJacoco("0.8.14")
  reports { /* unchanged — onCheck = true stays */ }
}
```

Local in-container run (set JAVA_HOME to your JDK):
`JAVA_HOME=/path/to/your/jdk ./gradlew test`
Coverage: `./gradlew koverXmlReport` → `build/reports/kover/report.xml`.

**Part C — byte-buddy force.** The `resolutionStrategy.force(...byte-buddy...:1.14.17)` that was on
this branch was NOT the retransform fix (the failure persisted with it). Empirically verified: tests
pass identically whether byte-buddy is forced to 1.14.17 or resolved naturally to 1.15.11 (via
MockK's transitive dependency). The force and its misleading "JBR retransform" comment have been
reverted.

## Tests (outside-in)

| ID | Layer | Assertion |
|----|-------|-----------|
| ACC-001 | Acceptance | A final-class MockK test passes in-container via `./gradlew test` (no flag); **no** `AttachNotSupportedException` AND **no** `attempted to delete a method` |
| ACC-002 | Acceptance | A `mockkStatic` test passes in-container with no flag |
| ACC-003 | Acceptance | Full `./gradlew test` suite green in-container with no flag; no test skipping needed |
| INT-001 | Integration | Effective test JVM args contain exactly one `-javaagent:byte-buddy-agent-*.jar` resolving to an existing file (RED if the line is removed) |
| INT-002 | Integration | Resolver selects the agent jar (not the core jar); fails loudly on a !=1 match (RED if pattern matches `byte-buddy`) |
| INT-003 | Integration | Test JVM receives the `jacoco-coverage-agent` arg AND no `kover-jvm-agent-*.jar` (RED if JaCoCo backend is removed) |
| INT-004 | Integration | `./gradlew koverXmlReport` produces `build/reports/kover/report.xml` with no flag |
| MAN-001 | Manual/CI | CI `test` job stays green on ubuntu/macos/windows AND still produces the Kover/JaCoCo report |

RED-first reference: the native Kover backend (pre-ADR-2) produced `attempted to delete a method`
in-container. The JaCoCo backend eliminates this. CI failure (pre-fix) was the confirmed red state.

## Out of scope / non-goals

- No `jdk.attach.*` / `StartAttachListener` flags, no `SYS_PTRACE`, no runtime switch off JBR (all
  disproven / impossible).
- No machine-specific JAVA_HOME baked into the build (would break CI/other devs).
- No new Kotlin/Java source; this is a build-config + docs + pre-push-hook change.
- Local in-container coverage is now available via `./gradlew koverXmlReport` (JaCoCo backend).
  The FOLLOW-UP from ADR-1 is resolved by ADR-2.

## Effort

| PR | Scope | Agent dev | Review | Manual | Total |
|----|-------|-----------|--------|--------|-------|
| PR-1 | JaCoCo backend (unconditional) + keep agent preload + isCliInstalled exec-bit fix + docs/pre-push-hook + verification | 0.5d | 1.0d | 0.5d | 2.0d |
