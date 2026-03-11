# Start Implementation Task

## Workflow Overview

```
Check Plan → [Create if missing] → TEST FIRST → Implement → Test & Lint → Commit → Session Hand-off
```

**TDD is NON-NEGOTIABLE**: Every code change requires a failing test BEFORE implementation.

## Phase 1: Initialize

### 1.1 Get Issue Context

```bash
git branch --show-current
```

The `issueID` follows format `XXX-XXXX` (e.g., `IDE-1718`).

### 1.2 Check for Implementation Plan

Look for: `${issueID}_implementation_plan/${issueID}_implementation_plan.md`

**If plan exists:** Read it, note current progress, continue from last checkpoint.

**If no plan:** Use `/create-implementation-plan` to create one.

---

## Phase 2: Planning

### Analysis

- **Files to modify:** [list files]
- **Files to create:** [list files]
- **Packages affected:** [list packages]

### Flow Diagrams

See: `docs/diagrams/${issueID}_*.png`

---

## Phase 3: Implementation (TDD)

### CRITICAL: TDD is MANDATORY

**NEVER write production code before writing a failing test.**

This applies to:
- New features
- Bug fixes
- Security fixes
- Refactoring
- ANY code change

### TDD Gate Check

Before writing ANY production code, verify:

- [ ] **Test exists?** Have I written a test for this change?
- [ ] **Test fails?** Does the test fail without my change?
- [ ] **Test is specific?** Does the test target the exact behavior I'm changing?

**If ANY answer is NO → STOP and write the test first.**

### TDD Cycle

For each feature/change:

1. **STOP** - Do not touch production code yet
2. **Write failing test first** (outside-in: start with integration/smoke tests, then unit tests)
3. **Run test** - confirm it fails for the right reason
4. **Write minimal code** to make `./gradlew test` pass
5. **Run test** - confirm it passes
6. **Refactor** if needed (tests must still pass)

### Test Order (Outside-In)

1. Smoke tests (E2E behavior)
2. Integration tests
3. Unit tests

### Commands

```bash
# Run unit tests
./gradlew test

# Run smoke tests / integration tests
./gradlew verifyPlugin

# Format and lint
./gradlew ktlintCheck spotlessApply
```

### Progress Updates

Before each step:

```markdown
| Step 1 | **in-progress** | Started [time] |
```

After each step:

```markdown
| Step 1 | **completed** | Finished [time] |
```

---

## Phase 4: Finalize

### 4.1 Run All Tests

```bash
./gradlew test
./gradlew verifyPlugin
```

All tests must pass. Fix any failures before proceeding.

### 4.2 Lint

```bash
./gradlew ktlintCheck spotlessApply
```

Zero linting errors required.

### 4.3 Security Scan

Run before commit:

- `snyk_code_scan` with absolute project path
- `snyk_sca_scan` with absolute project path

Fix any security issues (except in test data).

### 4.4 Generate & Update Docs

Update documentation in `./docs` as needed.

### 4.5 Commit

Pre-commit checklist:

- [ ] Tests pass (pre-existing issues MUST be fixed)
- [ ] Linting clean
- [ ] Security scans clean
- [ ] Docs updated

Commit format:

```
type(scope): description [XXX-XXXX]

Body explaining what and why.
```

**Never skip hooks. Never use --no-verify.**

---

## Phase 5: Session Hand-off

Update implementation plan with session summary according to the plan session handoff section.

---

## Quick Reference

| Action            | Command                               |
| ----------------- | ------------------------------------- |
| Unit tests        | `./gradlew test`                      |
| Smoke tests       | `./gradlew verifyPlugin`              |
| Format & lint     | `./gradlew ktlintCheck spotlessApply` |
| Coverage          | `./gradlew koverXmlReport`            |
