# Implementation Plan: {{TICKET_ID}} - {{TICKET_TITLE}}

---

## 🔄 SESSION RESUME (Read This First)

<!-- ALWAYS UPDATE THIS PLAN ALSO IN JIRA AND CONFLUENCE -->

> **For new agent sessions**: Read this section to quickly understand current state.

### Quick Context

- **Ticket**: {{TICKET_ID}} - {{TICKET_TITLE}} (https://snyksec.atlassian.net/browse/{{TICKET_ID}})
- **Branch**: `feat/{{TICKET_ID}}_{{branch-suffix}}`
- **Confluence Page**: {{CONFLUENCE_URL}} (if applicable)
- **Key Files**:
  - This plan: `{{TICKET_ID}}_implementation_plan.md`
  - Test tracking: `tests.json` (update after each test written/passed)
  - Diagrams: `docs/diagrams/{{TICKET_ID}}_*.png`, `docs/diagrams/{{TICKET_ID}}_*.mmd`

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
| Implementation Plan         | `{{TICKET_ID}}_implementation_plan.md` |
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

<!-- Describe the high-level goals of this implementation -->

This implementation plan covers:

1. <!-- Goal 1 -->
2. <!-- Goal 2 -->
3. <!-- Goal 3 -->

## References

- **JIRA Ticket**: [{{TICKET_ID}}](https://snyksec.atlassian.net/browse/{{TICKET_ID}})
- **Confluence**: <!-- Add link if applicable -->
- **Branch**: `feat/{{TICKET_ID}}_{{branch-suffix}}`
- **Test Scenarios**: `tests.json` (tracks all test scenarios across agent sessions)
- **Diagrams**: `docs/diagrams/{{TICKET_ID}}_*.mmd` / `.png`

### Key Commands

```bash
# Run before any changes
git checkout feat/{{TICKET_ID}}_{{branch-suffix}}
git pull origin feat/{{TICKET_ID}}_{{branch-suffix}}

# Run tests
./gradlew test -x detekt       # Unit tests

# lint
./gradlew detekt
```

---

## Phase 1: Planning

### 1.1 Requirements Analysis

#### Changes Required

<!-- List all changes needed - API, Database, etc. -->

- [ ] <!-- Change 1 -->
- [ ] <!-- Change 2 -->
- [ ] <!-- Change 3 -->

#### Error Handling

<!-- Define error scenarios and HTTP responses -->

| Scenario         | HTTP Status | Response                          |
| ---------------- | ----------- | --------------------------------- |
| <!-- Error 1 --> | 400         | `{"errors": [{"detail": "..."}]}` |
| <!-- Error 2 --> | 404         | `{"errors": [{"detail": "..."}]}` |
| <!-- Error 3 --> | 500         | `{"errors": [{"detail": "..."}]}` |

#### Files to Modify/Create

**New Files:**

- <!-- List new files to create -->

**Files to Modify:**

- <!-- List existing files to modify -->

### 1.2 Schema/Architecture Design

<!-- Add schema designs, data structures, etc. -->

```sql
-- Example SQL schema
```

```go
// Example Go structs
```

### 1.3 Flow Diagrams

<!-- Reference generated diagrams -->

![Diagram Name](docs/diagrams/{{TICKET_ID}}_diagram-name.png)

Generated diagrams located in:

- `docs/diagrams/{{TICKET_ID}}_*.mmd` / `.png`

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
> - Use conventional commit format: `type(scope): description [{{TICKET_ID}}]`
> - Ask for confirmation before making each atomic commit

### 2.1 Step Name [PENDING]

**📍 CHECKPOINT: Can be completed in one session**

**Steps:**

1. [ ] Step 1
2. [ ] Step 2
3. [ ] Run `./gradlew detekt` and fix any issues
4. [ ] Run tests (`./gradlew test -x detekt`)

**Tests (Write FIRST - TDD):** See `tests.json` IDs: `XX-001` to `XX-XXX`

- [ ] Test scenario 1
- [ ] Test scenario 2

**Commit when done:** `type(scope): description [{{TICKET_ID}}]`

### 2.2 Step Name [PENDING]

<!-- Repeat pattern for each implementation step -->

---

> 📋 **Test Tracking**: All test scenarios are tracked in `tests.json`
>
> - Update status as tests are written and pass
> - Reference test IDs (e.g., XX-001, XX-002) in commits
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

<!-- UPDATE THIS TABLE AT END OF EACH SESSION -->

| Phase | Step                       | Status         | Commit | Notes                   |
| ----- | -------------------------- | -------------- | ------ | ----------------------- |
| 1     | Planning                   | 🟡 In Progress | -      |                         |
| 1.1   | Requirements Analysis      | ⬜ Pending     | -      |                         |
| 1.2   | Schema/Architecture Design | ⬜ Pending     | -      |                         |
| 1.3   | Flow Diagrams              | ⬜ Pending     | -      |                         |
| 2     | Implementation             | ⬜ Pending     | -      |                         |
| 2.1   | Step Name                  | ⬜ Pending     | -      | Tests: XX-001 to XX-XXX |
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

<!-- ADD ENTRY AT END OF EACH SESSION -->

| Date       | Session | Work Done                   | Next Steps                      |
| ---------- | ------- | --------------------------- | ------------------------------- |
| YYYY-MM-DD | 1       | Created implementation plan | Get confirmation, start Phase 2 |

---

## Technical Notes

<!-- Add technical context, decisions, constraints -->

### Key Decisions Made

| Decision            | Resolution          |
| ------------------- | ------------------- |
| <!-- Decision 1 --> | <!-- Resolution --> |
| <!-- Decision 2 --> | <!-- Resolution --> |

### Critical Invariants (Must Always Be True)

1. <!-- Invariant 1 -->
2. <!-- Invariant 2 -->
3. **No cascade deletes** - all deletions must be explicit

---

## Open Points / Future Work

| Item            | Description          | Status     |
| --------------- | -------------------- | ---------- |
| <!-- Item 1 --> | <!-- Description --> | ⬜ Pending |

---

## Risks & Mitigations

| Risk            | Impact | Mitigation          |
| --------------- | ------ | ------------------- |
| <!-- Risk 1 --> | High   | <!-- Mitigation --> |
| <!-- Risk 2 --> | Medium | <!-- Mitigation --> |

---

## Estimated Effort

| Phase               | Estimated Time |
| ------------------- | -------------- |
| Planning            | X hours        |
| Implementation      | X hours        |
| Testing             | X hours        |
| Documentation       | X hours        |
| Code Review & Fixes | X hours        |
| **Total**           | **~X hours**   |

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
| Diagram        | `{{TICKET_ID}}_description.mmd` | `IDE-1234_api-flow.mmd`    |

---

## tests.json Template

Create a `tests.json` file with this structure (gitignored, do not commit):

```json
{
  "ticket": "{{TICKET_ID}}",
  "description": "{{TICKET_TITLE}}",
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
      "feature_name": {
        "status": "pending",
        "scenarios": [
          {
            "id": "XX-001",
            "name": "test_name",
            "description": "Test description",
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
