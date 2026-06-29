<!-- sub-plan: linked from docs/plans/PLAN.md -->

# Dev-container local code coverage via JaCoCo (follow-up to the MockK/ByteBuddy fix)

**Branch:** `fix/container-local-coverage-jacoco` (from `master`, after the MockK/ByteBuddy fix merges)
**Status:** Planning (analysis complete; recommendation ready for confirmation)
**Full spec:** engineering brain (private — Bastian only):
`github.com/snyk/snyk-intellij-plugin/plans/container-local-coverage-jacoco/overview.md`

## Problem

The merged MockK/ByteBuddy fix lets in-container developers run the test suite by setting
`-PdisableKoverInstrumentation`, which removes the Kover coverage agent to avoid the Kover↔MockK
retransform conflict — at the cost of **no local coverage**. This follow-up restores local coverage
without reintroducing the conflict and without changing CI.

## Decision

Add a **default-OFF** Gradle flag that makes the dev container measure coverage with **JaCoCo**
(`kover { useJacoco("0.8.14") }`) instead of the native Kover agent:

```kotlin
kover {
  if (project.hasProperty("koverUseJacoco")) {
    useJacoco("0.8.14")
  }
  // existing default-OFF -PdisableKoverInstrumentation guard stays as a zero-agent fallback
  // existing reports { } block unchanged (onCheck=true; report.xml path unchanged)
}
```

CI never sets the flag, so CI keeps measuring coverage with native Kover and the
`mi-kas/kover-report` PR gate (`min-coverage-overall: 40`, `min-coverage-changed-files: 80`) is
byte-for-byte unchanged. In-container, `./gradlew test koverXmlReport -PkoverUseJacoco` (with the
`JAVA_HOME` prefix) produces a real coverage report including the MockK-using tests.

## Why JaCoCo (evidence, all run in the LinuxKit container on the pinned JBR)

- Native Kover ON, `UtilsKtTest` → 12/16 fail (`attempted to delete a method`).
- Native Kover with `excludedClasses` (single class and full MockK set) → still 12–16 fail. Scoping
  does **not** work.
- Native Kover instrumenting ~nothing → conflict gone but no useful coverage.
- **Kover `useJacoco()`, MockK tests (`UtilsKtTest`, `CustomEndpointsTest`, `IgnoreServiceTest`) →
  zero "delete a method" errors; JaCoCo emits a 455 KB `.exec` with real app-class coverage data.**

## Honest scope limit

A separate, independent obstacle — IntelliJ `VFS can't be initialized` — fails ~131/185 in-container
tests regardless of coverage tool (confirmed with coverage OFF). Those tests contribute no local
coverage under any tool. This plan delivers coverage for the **passing** subset (now including MockK
tests); full local parity with CI requires fixing VFS separately (its own follow-up). The local
JaCoCo number is indicative, not byte-identical to CI's native-Kover number.

## Rejected alternatives (evidence in the brain spec)

native-Kover `excludedClasses`/`includedClasses` scoping (still conflicts / no coverage); two-pass
MockK/non-MockK split (non-MockK subset is VFS-blocked); unconditional `useJacoco()` (changes CI);
runtime switch / newer JBR / byte-buddy (disproven in ADR-1); JaCoCo offline (unneeded, not Kover-
supported).

## Tests (outside-in)

- **ACC:** MockK tests run under `-PkoverUseJacoco` with no attach/retransform error; `koverXmlReport`
  produces a non-empty report including MockK-exercised app classes; full-suite run produces a report
  with only the independent VFS/assertion failures remaining (no conflict).
- **INT:** with the flag, JaCoCo agent present / native Kover agent absent (and vice-versa without it);
  no-flag path still produces `report.xml` on `check`; JaCoCo version pinned.
- **Manual:** CI coverage gate + PR comment unchanged; local HTML report browsable; indicative-number
  and VFS caveats understood.

## Effort

1 PR, ~2.0d (0.5d dev + 1.0d review + 0.5d in-container/CI verification).
