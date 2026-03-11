# Commit Workflow

Prepare, verify, and commit code changes following project standards.

## Workflow Overview

```
Verify → Fix Issues (TDD) → Pre-commit Checks → Commit → Tests → [Push]
```

## Quick Start Checklist

Copy and track progress:

```
Commit Progress:
- [ ] Step 1: Run /verification
- [ ] Step 2: Fix issues using TDD
- [ ] Step 3: Run pre-commit checks
- [ ] Step 4: Create atomic commit
- [ ] Step 5: Run ALL test suites (3 REQUIRED):
      - [ ] ./gradlew ktlintCheck spotlessApply
      - [ ] ./gradlew koverXMLReport
      - [ ] ./gradlew verifyPlugin
- [ ] Step 6: Push (optional, ask first)
```

**CRITICAL: Step 5 has THREE mandatory test suites. Skipping any is FORBIDDEN.**

---

## Step 1: Run Verification

Execute the `/verification` command to analyze code changes:

```
Verification Progress:
- [ ] Load project rules and standards
- [ ] Trace code paths for modified files
- [ ] Check for semantic changes
- [ ] Identify code smells
- [ ] Run security scans
- [ ] Review PR feedback (if PR exists)
```

**Output**: List of issues found, categorized by severity.

---

## Step 2: Fix Issues Using TDD

For each issue identified by verification:

### TDD Gate (MANDATORY)

Before ANY fix:

- [ ] Write failing test first
- [ ] Confirm test fails without fix
- [ ] Implement minimal fix
- [ ] Confirm test passes

**Never skip TDD. Use `/implementation` for complex fixes.**

### Issue Priority

1. **Security vulnerabilities** - Fix immediately (except test data)
2. **Breaking changes** - Confirm intentional, add tests
3. **Code smells** - Fix immediately
4. **PR feedback** - Address blockers first

---

## Step 3: Pre-commit Checks

Run:

```bash
./gradlew ktlintCheck spotlessApply
./gradlew koverXMLReport
./gradlew verifyPlugin
```

### Security Scans

Get absolute path first:

```bash
pwd
```

Then run:

1. `snyk_sca_scan` with absolute project path
2. `snyk_code_scan` with absolute project path

**Fix any security issues** (skip test data false positives).

---

## Step 4: Create Atomic Commit

### Pre-commit Verification

```
- [ ] Linting clean (./gradlew ktlintCheck spotlessApply)
- [ ] Security scans clean
- [ ] No implementation plan files staged
- [ ] Documentation updated (if needed)
```

### Commit Format

```
type(scope): description [XXX-XXXX]

Body explaining what and why.
```

**Types**: feat, fix, refactor, test, docs, chore, perf

**Extract issue ID from branch:**

```bash
git branch --show-current
```

### Staged Files Check

```bash
git status
git diff --staged
```

**Never commit**:

- Implementation plan files (`*_implementation_plan/`)
- Secrets or credentials
- Generated diagram source (commit PNGs only if needed)

### Execute Commit

```bash
git add <files>
git commit -m "$(cat <<'EOF'
type(scope): description [XXX-XXXX]

Body explaining what and why.
EOF
)"
```

**NEVER use --no-verify. NEVER amend commits.**

---

## Step 5: Run All Test Suites (After Commit, Before Push)

**CRITICAL: ALL three test suites MUST be executed after commit but before push. Skipping ANY is FORBIDDEN.**

Run in order:

```bash
./gradlew ktlintCheck spotlessApply
./gradlew koverXMLReport
./gradlew verifyPlugin
```

If ANY test suite fails:

1. Do not proceed to push
2. Identify root cause
3. Apply TDD fix (test first, then implementation)
4. Create new commit with fix
5. Re-run ALL test suites

---

## Step 6: Push (Optional)

**Always ask before pushing.**

If approved:

```bash
git push --set-upstream origin $(git branch --show-current)
```

### After Push

Offer to:

1. Create draft PR (if none exists)
2. Update PR description (if PR exists)
3. Check snyk-pr-review-bot comments

---

## Command Reference

| Task                | Command                                            |
| ------------------- |----------------------------------------------------|
| Format & lint       | `./gradlew ktlintCheck spotlessApply`              |
| Unit tests          | `./gradlew test`                                   |
| Integration tests   | `./gradlew verifyPlugin`                           |
| SCA scan            | `snyk_sca_scan` with absolute path                 |
| Code scan           | `snyk_code_scan` with absolute path                |
| Current branch      | `git branch --show-current`                        |
| Push                | `git push --set-upstream origin $(git branch ...)` |

---

## Red Flags (STOP)

- [ ] Tests failing
- [ ] **Any test suite skipped**
- [ ] Security vulnerabilities unfixed
- [ ] Implementation plan files staged
- [ ] Unresolved PR blockers
- [ ] TDD not followed for fixes
- [ ] --no-verify being considered
