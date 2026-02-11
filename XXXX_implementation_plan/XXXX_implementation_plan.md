# Implementation Plan: XXXX - Add IntelliJ Platform UI tests (Remote Robot)

---

## 🔄 SESSION RESUME (Read This First)

<!-- ALWAYS UPDATE THIS PLAN ALSO IN JIRA AND CONFLUENCE -->

> **For new agent sessions**: Read this section to quickly understand current state.

### Quick Context

- **Ticket**: XXXX - Add IntelliJ Platform UI tests (Remote Robot) (https://snyksec.atlassian.net/browse/XXXX)
- **Branch**: `feat/XXXX_ui-tests-remote-robot`
- **Confluence Page**: N/A
- **Key Files**:
  - This plan: `XXXX_implementation_plan.md`
  - Test tracking: `tests.json` (update after each test written/passed)
  - Diagrams: `docs/diagrams/XXXX_*.png`, `docs/diagrams/XXXX_*.mmd`

### Rules

- ALWAYS FOLLOW THE RULES
- USE TDD to implement, use tests.json to keep track of the test scenarios.

### ⛔ CRITICAL: NEVER Circumvent Commit Hooks

**ABSOLUTELY FORBIDDEN:**

- ❌ `git commit --no-verify` - NEVER USE THIS
- ❌ Skipping `./gradlew test` before committing

**WHY**: The test hash mechanism ensures tests actually ran and passed. Manually
updating the hash or using `--no-verify` defeats this protection. If tests fail,
FIX THE TESTS - don't work around the commit hooks.

**IF COMMIT HOOK FAILS**: Fix the underlying issue. Do not bypass.

### Current State

<!-- UPDATE THIS SECTION AT END OF EACH SESSION -->

| Item                | Status                    |
| ------------------- | ------------------------- |
| **Current Phase**   | Phase 1: Planning         |
| **Current Step**    | 1.1 Requirements Analysis |
| **Last Commit**     | None                      |
| **Blocking Issues** | None                      |
| **Tests Passing**   | N/A                       |
| **Coverage**        | N/A                       |

### Quick State Check Commands

```bash
# Run these to verify state before starting work
git status                          # Check for uncommitted changes
git log --oneline -5                # See recent commits
./gradlew t                         # lint check
./gradlew test -x detekt            # Unit tests (fast)
cat tests.json | jq '.summary'      # See test summary
```

### CRITICAL: Before Committing

```bash
# ALWAYS run these before committing:
./gradlew test                      # test and lint
mcp_Snyk_snyk_code_scan             # Security scan (via MCP)
mcp_Snyk_snyk_sca_scan              # Dependency scan (via MCP)
```

⚠️ **NEVER use `--no-verify`!**
If the commit hook fails, FIX THE ISSUE - do not bypass the hook.

### Next Actions (Start Here)

<!-- UPDATE THIS LIST AT END OF EACH SESSION -->

1. ⬜ Phase 1.1: Requirements Analysis
2. ⬜ Phase 1.2: Schema/Architecture Design
3. ⬜ Phase 1.3: Flow Diagrams

### Current Working Files

<!-- UPDATE when changing focus -->

| Purpose                     | File                                   |
| --------------------------- | -------------------------------------- |
| Implementation Plan         | `XXXX_implementation_plan.md`          |
| Test Tracking               | `tests.json`                           |
| <!-- Add more as needed --> |                                        |

### Session Handoff Checklist

Before ending a session, the agent MUST:

- [ ] Update "Current State" table above
- [ ] Update "Current Working Files" table above
- [ ] Update "Next Actions" list above
- [ ] Update `tests.json` with test status changes (update `lastSession` object)
- [ ] Update Progress Tracking table below
- [ ] Run `./gradlew test` to catch any issues
- [ ] Commit any completed work (with user approval)
- [ ] Add comment to Jira ticket with progress
- [ ] Update Confluence page (if applicable)
- [ ] **CRITICAL**: When this implementation plan changes, also update Jira ticket description and Confluence page to stay in sync

### Recovery Procedures

If something goes wrong:

**Tests failing after refactoring:**

```bash
git stash                                        # Save current work
git checkout HEAD~1 -- internal/                 # Restore previous state
./gradlew test -x detekt                         # Verify tests pass
git stash pop                                    # Reapply changes carefully
```

---

## Overview

This implementation plan covers:

1. Adding a new `uiTest` source set with dedicated Gradle task and dependencies.
2. Wiring IntelliJ Platform UI Test (Remote Robot) runner for real UI tests.
3. Creating initial UI smoke tests and scheduling them to run nightly in CI.

## References

- **JIRA Ticket**: [XXXX](https://snyksec.atlassian.net/browse/XXXX)
- **Confluence**: N/A
- **Branch**: `feat/XXXX_ui-tests-remote-robot`
- **Test Scenarios**: `tests.json` (tracks all test scenarios across agent sessions)
- **Diagrams**: `docs/diagrams/XXXX_*.mmd` / `.png`

### Key Commands

```bash
# Run before any changes
git checkout feat/XXXX_ui-tests-remote-robot
git pull origin feat/XXXX_ui-tests-remote-robot

# Run tests
./gradlew test -x detekt       # Unit tests

# lint
./gradlew detekt
```

---

## Phase 1: Planning

### 1.1 Requirements Analysis

#### Changes Required

- [ ] Add new `uiTest` source set and Gradle task for UI tests.
- [ ] Add Remote Robot dependencies and test framework configuration.
- [ ] Add IDE runner task configured with Remote Robot server.
- [ ] Add initial UI smoke test(s) for key Snyk Tool Window flows.
- [ ] Add nightly CI job to execute `uiTest` task.

#### Usage Scenarios (UI Test Coverage)

- [ ] Clicking the Scan button triggers a scan and updates the tool window state.
- [ ] Clearing the cache updates UI state and removes stale results.
- [ ] Annotations render in-editor after a scan (highlighted issues).
- [ ] Code Vision lenses appear and update for issues.
- [ ] Clicking an issue tree node shows detailed information in the detail panel.
- [ ] Clicking an issue node navigates to the correct file and location.
- [ ] Filtering and sorting in the issues view updates the list correctly.

#### Error Handling

| Scenario                               | HTTP Status | Response                          |
| -------------------------------------- | ----------- | --------------------------------- |
| UI test IDE fails to launch            | 500         | `{"errors": [{"detail": "IDE launch failed"}]}` |
| Remote Robot cannot connect            | 500         | `{"errors": [{"detail": "Robot connection failed"}]}` |
| UI test assertion fails                | 400         | `{"errors": [{"detail": "UI assertion failed"}]}` |

#### Files to Modify/Create

**New Files:**

- `src/uiTest/kotlin/io/snyk/plugin/ui/...` (UI tests)
- `src/uiTest/resources/...` (fixtures if needed)
- `docs/diagrams/XXXX_ui-test-flow.mmd` (optional)

**Files to Modify:**

- `build.gradle.kts` (source set, dependencies, tasks)
- `.github/workflows/...` (nightly UI test workflow)
- `README.md` or `CONTRIBUTING.md` (how to run UI tests locally)

### 1.2 Schema/Architecture Design

```text
No schema changes. UI tests will run against a dedicated IDE instance with Remote Robot server enabled.
```

### 1.3 Flow Diagrams

![UI Test Flow](docs/diagrams/XXXX_ui-test-flow.png)

Generated diagrams located in:

- `docs/diagrams/XXXX_*.mmd` / `.png`

---

## Phase 2: Implementation (TDD)

### Implementation Principles

> ⚠️ **CRITICAL: No Copy-Paste of Logic**
>
> - **DO NOT** copy logic from existing versions
> - **REFACTOR** shared logic into reusable packages/functions
> - **EXTRACT** common functionality to shared locations
> - **TDD** must ensure no regressions in existing functionality
> - Run tests for ALL affected areas after any refactoring

> 📦 **ATOMIC COMMITS**
>
> - Each commit must be self-contained and functional
> - One logical change per commit
> - Tests must pass before each commit
> - Use conventional commit format: `type(scope): description [XXXX]`
> - Ask for confirmation before making each atomic commit

### 2.1 Add UI test source set and dependencies [PENDING]

**📍 CHECKPOINT: Can be completed in one session**

**Steps:**

1. [ ] Add `uiTest` source set and configurations in `build.gradle.kts`.
2. [ ] Add Remote Robot dependencies to `uiTestImplementation`.
3. [ ] Register `uiTest` task bound to IDE runner with robot server enabled.
4. [ ] Run `./gradlew detekt` and fix any issues.
5. [ ] Run tests (`./gradlew test -x detekt`).

**Tests (Write FIRST - TDD):** See `tests.json` IDs: `XXXX-001` to `XXXX-0XX`

- [ ] Verify `uiTest` task starts IDE with robot server.
- [ ] Verify `uiTest` source set compiles.

**Commit when done:** `build(test): add uiTest source set and robot deps [XXXX]`

### 2.2 Add UI smoke tests [PENDING]

**Steps:**

1. [ ] Create baseline Remote Robot test fixtures (IDE launch + window focus).
2. [ ] Add Snyk Tool Window UI smoke test (open tool window, verify panels).
3. [ ] Add test for auth panel visibility when token missing (if feasible).
4. [ ] Run `./gradlew uiTest` locally.

**Usage Scenarios Implemented in This Step:**

1. [ ] Click Scan button → scan triggers and tool window updates.
2. [ ] Clear cache → UI state resets and results cleared.
3. [ ] Annotations render in-editor after scan.
4. [ ] Code Vision lenses appear/update for issues.
5. [ ] Click issue tree node → details panel shows issue info.
6. [ ] Click issue node → navigates to file + location.
7. [ ] Filtering & sorting update issue list correctly.

**Tests (Write FIRST - TDD):** See `tests.json` IDs: `XXXX-010` to `XXXX-0XX`

- [ ] Smoke: tool window opens and renders expected root panels.
- [ ] Smoke: auth panel renders when token missing.

**Commit when done:** `test(ui): add initial Remote Robot UI tests [XXXX]`

### 2.3 Nightly CI UI tests [PENDING]

**Steps:**

1. [ ] Add nightly workflow to run `uiTest` task.
2. [ ] Archive IDE logs and screenshots on failure.
3. [ ] Document how to run UI tests locally.

**Tests (Write FIRST - TDD):** See `tests.json` IDs: `XXXX-020` to `XXXX-0XX`

- [ ] CI workflow triggers nightly on schedule.
- [ ] UI tests run headless with logs available on failure.

**Commit when done:** `ci(ui): add nightly uiTest workflow [XXXX]`

---

> 📋 **Test Tracking**: All test scenarios are tracked in `tests.json`
>
> - Update status as tests are written and pass
> - Reference test IDs (e.g., XXXX-001, XXXX-002) in commits
> - Do not commit `tests.json` (gitignored)
> - Update `tests.json` at end of EVERY session

---

## Phase 3: Review

### 3.1 Code Review Preparation [PENDING]

- [ ] Run `./gradlew test` and fix any issues
- [ ] Run Snyk security scans (`snyk_sca_scan`, `snyk_code_scan`)

### 3.2 Documentation Updates [PENDING]

- [ ] Update relevant docs in `docs/`
- [ ] Update Confluence page with implementation details
- [ ] Generate and add flow diagrams to documentation

### 3.3 Pre-Commit Checks [PENDING]

- [ ] Verify no uncommitted generated files
- [ ] Update Jira ticket with progress

---

## Progress Tracking

### Current Status: Phase 1 - Planning

| Phase | Step                       | Status         | Commit | Notes                   |
| ----- | -------------------------- | -------------- | ------ | ----------------------- |
| 1     | Planning                   | 🟡 In Progress | -      |                         |
| 1.1   | Requirements Analysis      | ⬜ Pending     | -      |                         |
| 1.2   | Schema/Architecture Design | ⬜ Pending     | -      |                         |
| 1.3   | Flow Diagrams              | ⬜ Pending     | -      |                         |
| 2     | Implementation             | ⬜ Pending     | -      |                         |
| 2.1   | Add uiTest setup            | ⬜ Pending     | -      | Tests: XXXX-001 to 0XX  |
| 2.2   | Add UI smoke tests          | ⬜ Pending     | -      | Tests: XXXX-010 to 0XX  |
| 2.3   | Nightly CI UI tests          | ⬜ Pending     | -      | Tests: XXXX-020 to 0XX  |
| 3     | Review                     | ⬜ Pending     | -      |                         |
| 3.1   | Code Review Prep           | ⬜ Pending     | -      |                         |
| 3.2   | Documentation              | ⬜ Pending     | -      |                         |
| 3.3   | Pre-Commit Checks          | ⬜ Pending     | -      |                         |

### Legend

- ⬜ Pending
- 🟡 In Progress
- ✅ Complete
- ❌ Blocked

### Session Log

| Date       | Session | Work Done                   | Next Steps                      |
| ---------- | ------- | --------------------------- | ------------------------------- |
| 2026-01-27 | 1       | Created implementation plan | Confirm + start Phase 2         |

---

## Technical Notes

### Key Decisions Made

| Decision                          | Resolution                                 |
| --------------------------------- | ------------------------------------------ |
| UI test framework                 | IntelliJ Platform UI tests via Remote Robot |
| Source set                         | New `uiTest` source set                     |
| CI cadence                         | Nightly only                                |

### Critical Invariants (Must Always Be True)

1. UI tests must use a dedicated IDE instance with Remote Robot server enabled.
2. UI tests must be isolated from unit/integration tests (`uiTest` task only).
3. **No cascade deletes** - all deletions must be explicit

---

## Open Points / Future Work

| Item            | Description                      | Status     |
| --------------- |----------------------------------| ---------- |
| CI resources    | Confirm IDE license/runner needs | ⬜ Pending |
| Test environment| headless                         | ⬜ Pending |

---

## Risks & Mitigations

| Risk                                | Impact | Mitigation                                |
| ----------------------------------- | ------ | ----------------------------------------- |
| UI test flakiness                    | High   | Add robust waits and stable selectors     |
| IDE startup time in CI               | Medium | Nightly schedule, cache IDE downloads     |
| Remote Robot API changes             | Medium | Pin compatible version and document usage |

---

## Estimated Effort

| Phase               | Estimated Time |
| ------------------- | -------------- |
| Planning            | 2 hours        |
| Implementation      | 1-2 days       |
| Testing             | 1 day          |
| Documentation       | 0.5 day        |
| Code Review & Fixes | 0.5 day        |
| **Total**           | **~3-5 days**  |

---

## Quick Reference Card

### Test Coverage Requirements

- Unit tests: 80%+ coverage
- Integration tests: All scenarios in `tests.json`
- Regression tests: ALL existing tests must pass

### File Naming Conventions

| Type           | Pattern                         | Example                    |
| -------------- | ------------------------------- | -------------------------- |
| Migration up   | `NNN_description.up.sql`        | `007_add_feature.up.sql`   |
| Migration down | `NNN_description.down.sql`      | `007_add_feature.down.sql` |
| Handler        | `handler.go`                    | `2025-XX-XX/handler.go`    |
| Spec           | `spec.yaml`                     | `2025-XX-XX/spec.yaml`     |
| Test           | `*_test.go`                     | `feature_test.go`          |
| Diagram        | `XXXX_description.mmd`          | `XXXX_ui-test-flow.mmd`    |

---

## tests.json Template

Create a `tests.json` file with this structure (gitignored, do not commit):

```json
{
  "ticket": "XXXX",
  "description": "Add IntelliJ Platform UI tests (Remote Robot)",
  "lastUpdated": "YYYY-MM-DD",
  "lastSession": {
    "date": "YYYY-MM-DD",
    "sessionNumber": 1,
    "completedSteps": [],
    "currentStep": "1.1 Requirements Analysis",
    "nextStep": "1.2 Schema Design"
  },
  "testSuites": {
    "unit": {
      "ui_test_infrastructure": {
        "status": "pending",
        "scenarios": [
          {
            "id": "XXXX-001",
            "name": "uiTest_task_launches_IDE",
            "description": "UI test task launches IDE with robot server",
            "status": "pending"
          }
        ]
      }
    },
    "integration": {
      "scenarios": []
    },
    "regression": {
      "scenarios": []
    }
  }
}
```
