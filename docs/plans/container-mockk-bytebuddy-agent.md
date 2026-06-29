<!-- sub-plan: linked from docs/plans/PLAN.md -->

# ByteBuddy preload + Kover-instrumentation opt-out so MockK tests run in the dev container

**Ticket:** none assigned
**Branch:** `fix/container-mockk-bytebuddy-agent` (from `master`)
**Status:** Planning
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

**Part B — Kover instrumentation opt-out (the new work).** In the existing `kover { }` block, add a
**default-OFF** guard that disables Kover's test instrumentation only when the explicit Gradle property
`-PdisableKoverInstrumentation` is set:

```kotlin
kover {
  if (project.hasProperty("disableKoverInstrumentation")) {
    currentProject { instrumentation { disabledForAll = true } }
  }
  reports { /* unchanged — onCheck = true stays */ }
}
```

Default = flag absent = **Kover ON**. CI runs `./gradlew clean ktlintCheck spotlessCheck check`
without the flag, so the agent set, coverage report, and outcomes are byte-for-byte unchanged on all
three OS legs. The dev container and its pre-push hook set the flag. No environment sniffing.

**Part C — byte-buddy force.** The `resolutionStrategy.force(...byte-buddy...:1.14.17)` that was on
this branch was NOT the retransform fix (the failure persisted with it). Empirically verified: with
`-PdisableKoverInstrumentation` set, tests pass identically whether byte-buddy is forced to 1.14.17
or resolved naturally to 1.15.11 (via MockK's transitive dependency). The force and its misleading
"JBR retransform" comment have been reverted.

Local in-container run (Linux dev-container only; `readlink -f` is not portable to macOS):
`JAVA_HOME="$HOME/.sdkman/candidates/java/current" ./gradlew test -PdisableKoverInstrumentation`.

## Tests (outside-in)

| ID | Layer | Assertion |
|----|-------|-----------|
| ACC-001 | Acceptance | A final-class MockK test passes in-container via `./gradlew test -PdisableKoverInstrumentation`; **no** `AttachNotSupportedException` AND **no** `attempted to delete a method` |
| ACC-002 | Acceptance | A `mockkStatic` test passes in-container with the flag |
| ACC-003 | Acceptance | Full `./gradlew test -PdisableKoverInstrumentation` suite green in-container; no test skipping needed |
| INT-001 | Integration | Effective test JVM args contain exactly one `-javaagent:byte-buddy-agent-*.jar` resolving to an existing file (RED if the line is removed) |
| INT-002 | Integration | Resolver selects the agent jar (not the core jar); fails loudly on a !=1 match (RED if pattern matches `byte-buddy`) |
| INT-003 | Integration | `kover-jvm-agent-*.jar` is ABSENT from the test JVM args WITH the flag and PRESENT WITHOUT it (RED if the guard is removed or made unconditional) |
| MAN-001 | Manual/CI | CI `test` job stays green on ubuntu/macos/windows AND still produces the Kover report (CI + coverage unchanged) |

RED-first: on the branch as-is (agent preloaded, Kover still ON), ACC-001/002 must fail in-container
with `attempted to delete a method` (proving the Kover-MockK conflict) before the opt-out is added.

## Out of scope / non-goals

- No `jdk.attach.*` / `StartAttachListener` flags, no `SYS_PTRACE`, no runtime switch off JBR (all
  disproven / impossible).
- No machine-specific JAVA_HOME baked into the build (would break CI/other devs).
- No new Kotlin/Java source; this is a build-config + docs + pre-push-hook change.
- **FOLLOW-UP (not solved here):** the opt-out means local in-container runs produce no Kover coverage.
  A later plan must restore local in-container coverage without reintroducing the Kover-MockK conflict.

## Effort

| PR | Scope | Agent dev | Review | Manual | Total |
|----|-------|-----------|--------|--------|-------|
| PR-1 | Kover opt-out (default-OFF) + keep agent preload + byte-buddy-force verification + docs/pre-push-hook + verification | 0.5d | 1.0d | 0.5d | 2.0d |
