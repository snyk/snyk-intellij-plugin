# Implementation Plan: IDE-1692 - Migrate from Detekt to Ktfmt + Ktlint

---

## 🔄 SESSION RESUME (Read This First)

### Quick Context

- **Ticket**: IDE-1692 - Migrate from Detekt to Ktfmt + Ktlint (https://snyksec.atlassian.net/browse/IDE-1692)
- **Branch**: `chore/IDE-1692_update-linting-and-formatting-in-intellij`
- **Key Files**:
  - This plan: `IDE-1692_implementation_plan.md`
  - Diagrams: `docs/diagrams/IDE-1692_*.mmd`, `docs/diagrams/IDE-1692_*.png`

### Rules

- ALWAYS FOLLOW THE RULES
- No TDD for this change (no production logic; tooling/config only). Verification: run `./gradlew spotlessCheck ktlintCheck` and `./gradlew test`.

### ⛔ CRITICAL: NEVER Circumvent Commit Hooks

- ❌ `git commit --no-verify` - NEVER USE THIS
- If commit hook fails, FIX THE ISSUE - do not bypass.

### Current State

| Item                | Status                    |
| ------------------- | ------------------------- |
| **Current Phase**   | Phase 1: Planning         |
| **Current Step**    | 1.1 Requirements Analysis |
| **Last Commit**     | None                      |
| **Blocking Issues** | None                      |

### Quick State Check Commands

```bash
git status
git log --oneline -5
./gradlew spotlessCheck ktlintCheck   # after migration
./gradlew test                        # full test suite
```

### Next Actions (Start Here)

1. ⬜ Get confirmation that the implementation plan is OK
2. ⬜ Phase 2.1: Build system updates (remove Detekt, add Spotless + Ktlint)
3. ⬜ Phase 2.2: .editorconfig updates
4. ⬜ Phase 2.3: CI/CD workflow updates
5. ⬜ Phase 2.4: Codebase migration (spotlessApply, .git-blame-ignore-revs, ktlintCheck)
6. ⬜ Phase 2.5: Documentation and pre-commit hook

---

## Overview

Migrate from Detekt (linting + formatting) to:

- **Ktfmt** (via Spotless): deterministic, Google-style formatting only.
- **Ktlint**: code style enforcement only (no formatting; formatting left to Spotless/Ktfmt).

This separates formatting from linting, aligns with modern Kotlin tooling, and reduces configuration churn.

## References

- **JIRA Ticket**: [IDE-1692](https://snyksec.atlassian.net/browse/IDE-1692)
- **Branch**: `chore/IDE-1692_update-linting-and-formatting-in-intellij`
- **Diagrams**: `docs/diagrams/IDE-1692_*.mmd` / `.png`

### Key Commands (Post-Migration)

```bash
./gradlew spotlessCheck    # check formatting
./gradlew spotlessApply    # apply formatting
./gradlew ktlintCheck      # lint only (no format)
./gradlew test             # tests
```

---

## Phase 1: Planning

### 1.1 Requirements Analysis

#### Changes Required

- [ ] Remove Detekt plugin and all Detekt configuration from `build.gradle.kts`.
- [ ] Add Spotless plugin (com.diffplug.spotless) with ktfmt ≥ 0.47, Google Style.
- [ ] Add Ktlint Gradle plugin (org.jlleitschuh.gradle.ktlint) version 12.x, configured so formatting is not applied by Ktlint (Spotless/Ktfmt only).
- [ ] Update `.editorconfig`: disable ktlint formatting rules (indent, final-newline, import-ordering, etc.), enable strict lint rules (e.g. no-unused-imports, package-name), set `indent_size = 2` for Kotlin.
- [ ] Update CI: replace detekt job/tasks with `./gradlew spotlessCheck` and `./gradlew ktlintCheck`; update build.yml and release.yml to drop `-x detekt` and use new checks where appropriate.
- [ ] Run `./gradlew spotlessApply` once; add that commit SHA to `.git-blame-ignore-revs`.
- [ ] Run `./gradlew ktlintCheck` and fix issues (or add baseline if necessary).
- [ ] Update CONTRIBUTING.md with "How to Format" and ktfmt IntelliJ plugin (Google Style).
- [ ] Add pre-commit hook: `./gradlew spotlessCheck ktlintCheck`.
- [ ] Update AGENT.md, .cursor/rules/general.mdc to reference spotless/ktlint instead of detekt.
- [ ] Remove `.github/detekt/` and detekt-related workflow (or repurpose detekt.yml to "Lint" running spotlessCheck + ktlintCheck).

#### Files to Modify/Create

**New Files:**

- `.git-blame-ignore-revs` (after reformat commit)
- Optional: `docs/how-to-format.md` or section in CONTRIBUTING

**Files to Modify:**

- `build.gradle.kts` (plugins, deps, remove detekt block and Detekt task config)
- `.editorconfig` (Kotlin: indent_size=2, ktlint rule toggles)
- `.github/workflows/build.yml` (check → spotlessCheck + ktlintCheck, remove -x detekt)
- `.github/workflows/detekt.yml` (rename/repurpose to lint job: spotlessCheck + ktlintCheck; remove SARIF from detekt or add ktlint SARIF if supported)
- `.github/workflows/release.yml` (replace `check -x detekt` with check that includes spotless + ktlint)
- `CONTRIBUTING.md` (How to Format, ktfmt IntelliJ plugin)
- `.pre-commit-config.yaml` (add spotlessCheck + ktlintCheck; update test hook to remove -x detekt)
- `AGENT.md` (replace detekt with spotless/ktlint)
- `.cursor/rules/general.mdc` (replace detekt with spotless/ktlint)

**Files/Dirs to Remove (after migration):**

- `.github/detekt/detekt-config.yml`
- `.github/detekt/detekt-baseline.xml`
- Detekt-specific workflow steps (or entire detekt.yml if replaced by one "Lint" job)

### 1.2 Schema/Architecture Design

- **Formatting**: Spotless + ktfmt (Google Style), single source of truth for layout.
- **Linting**: Ktlint standard rules only; formatting-related rules disabled in .editorconfig to avoid clashes with ktfmt.
- **CI**: One or two jobs run `spotlessCheck` and `ktlintCheck`; no Detekt.

### 1.3 Flow Diagrams

**CI format and lint flow:**

![CI Format Lint Flow](docs/diagrams/IDE-1692_ci-format-lint-flow.png)

**Developer and pre-commit flow:**

![Developer Format Lint Flow](docs/diagrams/IDE-1692_developer-format-lint-flow.png)

Generated diagrams:

- `docs/diagrams/IDE-1692_ci-format-lint-flow.mmd` / `.png`
- `docs/diagrams/IDE-1692_developer-format-lint-flow.mmd` / `.png`

---

## Phase 2: Implementation

### 2.1 Build system updates (remove Detekt, add Spotless + Ktlint) [PENDING]

**Steps:**

1. [ ] In `build.gradle.kts`: remove `import io.gitlab.arturbosch.detekt.Detekt`.
2. [ ] Remove `id("io.gitlab.arturbosch.detekt")` and `detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")`.
3. [ ] Add `id("com.diffplug.spotless") version "8.2.1"` (or current 8.x).
4. [ ] Add `id("org.jlleitschuh.gradle.ktlint") version "12.1.0"` (or latest 12.x).
5. [ ] Remove entire `detekt { ... }` block.
6. [ ] Remove `withType<Detekt> { ... }` block.
7. [ ] Add Spotless configuration: Kotlin/KotlinScript with ktfmt 0.47+ (or 0.50.x), Google style.
8. [ ] Configure Ktlint so it does not run formatting (e.g. only apply to check; no format task in default pipeline, or disable format task). Rely on .editorconfig to disable formatting rules.
9. [ ] Run `./gradlew spotlessCheck` and `./gradlew ktlintCheck` (expect failures until editorconfig and codebase are updated).
10. [ ] Run `./gradlew test` (without detekt) and fix any build breakage.

**Commit when done:** `build(build.gradle): replace Detekt with Spotless (ktfmt) and Ktlint [IDE-1692]`

### 2.2 .editorconfig updates [PENDING]

**Steps:**

1. [ ] Under `[*.{kt,kts}]`: set `indent_size = 2` (Google Style).
2. [ ] Disable ktlint formatting rules that conflict with ktfmt (e.g. `ktlint_standard_indent`, `ktlint_standard_final-newline`, `ktlint_standard_import-ordering`, `ktlint_standard_no-trailing-whitespace` if they would conflict).
3. [ ] Enable or keep strict lint rules (e.g. `no-unused-imports`, `package-name`); ensure they are not disabled by current `ktlint_standard = disabled` if we only disable selected rules.
4. [ ] Align with ktlint 12.x .editorconfig property names (`ktlint_standard_<rule-id> = disabled` / `enabled`).
5. [ ] Re-run `./gradlew ktlintCheck` and fix any new violations or adjust .editorconfig.

**Commit when done:** `chore(editorconfig): align Kotlin with ktfmt/ktlint, indent_size=2 [IDE-1692]`

### 2.3 CI/CD workflow updates [PENDING]

**Steps:**

1. [ ] In `.github/workflows/build.yml`: replace `./gradlew clean check -x detekt` with `./gradlew clean check` (ensure `check` depends on `spotlessCheck` and `ktlintCheck`), or add explicit `spotlessCheck` and `ktlintCheck` steps.
2. [ ] In `.github/workflows/detekt.yml`: replace detekt step with `./gradlew spotlessCheck` and `./gradlew ktlintCheck`. Remove or adapt SARIF upload (detekt.sarif); if ktlint outputs SARIF, use that; otherwise remove SARIF step or keep job without SARIF.
3. [ ] In `.github/workflows/release.yml`: replace `./gradlew clean check -x detekt` with `./gradlew clean check` (so release runs spotless + ktlint).
4. [ ] Verify workflow YAML syntax and that job names/steps are clear.

**Commit when done:** `ci(workflows): run spotlessCheck and ktlintCheck instead of detekt [IDE-1692]`

### 2.4 Codebase migration (spotlessApply, blame revs, ktlint) [PENDING]

**Steps:**

1. [ ] Run `./gradlew spotlessApply` and review diff.
2. [ ] Commit reformatting only: `style: apply ktfmt Google style (spotlessApply) [IDE-1692]`.
3. [ ] Create `.git-blame-ignore-revs` and add the reformat commit SHA (full 40-char hash).
4. [ ] Run `./gradlew ktlintCheck` and fix remaining issues; if volume is high, consider baseline and document follow-up to reduce it.
5. [ ] Commit ktlint fixes and `.git-blame-ignore-revs`: `chore(lint): add .git-blame-ignore-revs and fix ktlint [IDE-1692]`.

**Commit(s):** One for reformat + one for blame file and ktlint fixes (or split as needed).

### 2.5 Documentation and pre-commit hook [PENDING]

**Steps:**

1. [ ] **CONTRIBUTING.md**: Add "How to Format" section: install ktfmt IntelliJ plugin, set to Google Style; run `./gradlew spotlessApply` before committing; CI runs `spotlessCheck` and `ktlintCheck`.
2. [ ] **Pre-commit**: In `.pre-commit-config.yaml`, add a hook that runs `./gradlew spotlessCheck ktlintCheck` (and fix task name casing: `ktlintCheck`). Update existing test hook to run `./gradlew test` (no `-x detekt`).
3. [ ] **AGENT.md**: Replace references to `detekt` with `spotlessCheck`/`ktlintCheck` and `spotlessApply`.
4. [ ] **.cursor/rules/general.mdc**: Same replacement (run `./gradlew detekt` → run `./gradlew spotlessCheck ktlintCheck`; `test -x detekt` → `test`).
5. [ ] Ensure `check` task depends on `spotlessCheck` and `ktlintCheck` so `./gradlew check` runs them (Spotless and Ktlint plugins usually add this).

**Commit when done:** `docs: how to format (ktfmt) and pre-commit spotless+ktlint [IDE-1692]`

### 2.6 Remove Detekt config and optional cleanup [PENDING]

**Steps:**

1. [ ] Delete `.github/detekt/detekt-config.yml` and `.github/detekt/detekt-baseline.xml` (or move to archive; plan says remove).
2. [ ] If detekt.yml is fully replaced by a "Lint" job, remove references to `build/detekt.sarif` and any detekt-specific steps.
3. [ ] Run full test suite and `./gradlew verifyPlugin` once.
4. [ ] Update implementation plan template (`.github/IMPLEMENTATION_PLAN_TEMPLATE.md`) if it references detekt in Key Commands / steps (optional, can be separate cleanup).

**Commit when done:** `chore: remove Detekt config and baseline [IDE-1692]`

---

## Phase 3: Review

### 3.1 Code review preparation [PENDING]

- [ ] Run `./gradlew test` and fix any failures.
- [ ] Run `./gradlew spotlessCheck ktlintCheck` (and `verifyPlugin` if desired).
- [ ] Run Snyk security scans (`snyk_sca_scan`, `snyk_code_scan`) before committing.

### 3.2 Documentation [PENDING]

- [ ] CONTRIBUTING.md "How to Format" and ktfmt plugin instructions are clear.
- [ ] No stale references to Detekt in docs or agent rules.

### 3.3 Pre-commit checks [PENDING]

- [ ] Pre-commit hook runs `spotlessCheck ktlintCheck` (and test hook runs `test`).
- [ ] `.git-blame-ignore-revs` contains the reformat commit SHA.

---

## Progress Tracking

| Phase | Step                              | Status         | Commit | Notes |
| ----- | --------------------------------- | -------------- | ------ | ----- |
| 1     | Planning                          | ✅ Complete    | -      |       |
| 1.1   | Requirements Analysis             | ✅ Complete    | -      |       |
| 1.2   | Schema/Architecture Design        | ✅ Complete    | -      |       |
| 1.3   | Flow Diagrams                      | ✅ Complete    | -      |       |
| 2     | Implementation                    | ✅ Complete   | -      |       |
| 2.1   | Build system updates              | ✅ Complete   | -      |       |
| 2.2   | .editorconfig                     | ✅ Complete   | -      |       |
| 2.3   | CI/CD workflows                   | ✅ Complete   | -      |       |
| 2.4   | Codebase migration + blame revs   | ✅ Complete   | -      | Add reformat SHA to .git-blame-ignore-revs after first commit |
| 2.5   | Documentation + pre-commit       | ✅ Complete   | -      |       |
| 2.6   | Remove Detekt config              | ✅ Complete   | -      |       |
| 3     | Review                            | ⬜ Pending     | -      |       |
| 3.1   | Code review prep                  | ⬜ Pending     | -      |       |
| 3.2   | Documentation                     | ⬜ Pending     | -      |       |
| 3.3   | Pre-commit checks                 | ⬜ Pending     | -      |       |

### Legend

- ⬜ Pending | 🟡 In Progress | ✅ Complete | ❌ Blocked

---

## Technical Notes

### Key Decisions

| Decision                    | Resolution                                                                 |
| --------------------------- | -------------------------------------------------------------------------- |
| Formatting tool             | Spotless + ktfmt (Google Style), version 0.47+                             |
| Linting tool                | Ktlint 12.x, check-only; formatting rules disabled in .editorconfig        |
| Kotlin indent               | 2 spaces (Google Style)                                                    |
| Blame history               | Reformat commit SHA in `.git-blame-ignore-revs`                            |
| CI                          | Replace detekt with spotlessCheck + ktlintCheck                            |
| Pre-commit                  | Add `spotlessCheck ktlintCheck`; test hook runs `./gradlew test` (no -x detekt) |

### Risks & Mitigations

| Risk              | Impact | Mitigation                                                                 |
| ----------------- | ------ | -------------------------------------------------------------------------- |
| Merge conflicts   | High   | Merge this PR quickly or coordinate code freeze; document in PR.           |
| ktfmt version     | Medium | Pin ktfmt version in Spotless to match recommended IntelliJ ktfmt plugin.   |
| ktlint volume     | Medium | Fix issues or add baseline and plan follow-up to reduce baseline.          |

---

## Acceptance Criteria (Checklist)

- [ ] `build.gradle.kts` contains Spotless (Ktfmt) and Ktlint plugins; Detekt is removed.
- [ ] `./gradlew spotlessCheck` passes on CI.
- [ ] `./gradlew ktlintCheck` passes on CI.
- [ ] Codebase is consistently formatted with 2-space indentation (Google Style).
- [ ] `.git-blame-ignore-revs` exists and contains the migration (reformat) commit.
- [ ] A "How to Format" section exists in repository documentation (CONTRIBUTING.md).
