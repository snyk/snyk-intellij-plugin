# Code Verification

Verify generated code in depth before committing. This command complements the pre-commit checklist by adding semantic analysis.

## When to Use

- Before committing implementation changes
- After completing implementation steps
- When PR review feedback needs to be addressed
- When explicitly asked to verify code
- When starting a new session of an implementation plan

## Verification Workflow

Copy this checklist and track progress:

```
Verification Progress:
- [ ] Step 1: Load project rules and standards
- [ ] Step 2: Trace code paths for modified files
- [ ] Step 3: Check for semantic changes
- [ ] Step 4: Identify code smells
- [ ] Step 5: Run security scans
- [ ] Step 6: Review PR feedback (if PR exists)
- [ ] Step 7: Get check results from github with gh cli
- [ ] Step 8: Update implementation plan with findings
- [ ] Step 9: Fix issues (TDD REQUIRED - test first, then fix)
- [ ] Step 10: Check coverage of changed files > 80%
- [ ] Step 11: Add tests if coverage not sufficient
- [ ] Step 12: Commit changes
```

---

## Step 1: Load Project Rules

Read and apply these project standards:

1. `CLAUDE.md` - critical rules and workflow
2. `.github/CONTRIBUTING.md` - coding standards

Key rules to verify against:

- Outside-in TDD followed
- Minimum necessary changes
- No workarounds or commented-out code
- mockk used for mocking (no custom mocks)

---

## Step 2: Trace Code Paths

For each modified file, trace the execution flow:

1. **Identify entry points**: API handlers, public functions, exported methods
2. **Follow the call chain**: Map function calls through the codebase
3. **Verify dependencies**: Check that all called functions exist and have correct signatures
4. **Check return paths**: Ensure all code paths return appropriate values/errors

### Verification Questions

- Does the new code integrate correctly with existing callers?
- Are all error cases handled?
- Do interface implementations satisfy their contracts?
- Are there unreachable code paths?

---

## Step 3: Check for Semantic Changes

Detect unintended behavioral changes:

### Breaking Changes

- Modified function signatures
- Changed return types or error conditions
- Altered class/data class field types
- Modified interface definitions

### Behavioral Changes

- Different error messages (may break client parsing)
- Changed response structure
- Modified validation logic
- Altered default values

### API Impact

- API contract changes requiring versioning
- Behavior changes requiring documentation updates

**Action**: Flag any semantic changes and ask if they are intentional.

---

## Step 4: Identify Code Smells

Check for these patterns:

### Structural Smells

- [ ] Functions longer than 50 lines
- [ ] Deeply nested conditionals (>3 levels)
- [ ] Duplicate code blocks
- [ ] God objects/functions doing too much
- [ ] Long parameter lists (>5 params)

### Kotlin-Specific Smells

- [ ] Ignored exceptions (empty catch blocks)
- [ ] Non-null assertions (`!!`) without justification
- [ ] Mutable state shared across threads without synchronization
- [ ] Memory leaks (listeners not unregistered, resources not closed)

### Design Smells

- [ ] Circular dependencies between packages
- [ ] Leaky abstractions (implementation details exposed)
- [ ] Inappropriate intimacy (packages knowing too much about each other)
- [ ] Feature envy (functions using other package's data excessively)

### Copy-Paste Code (Refactoring Candidates)

Identify duplicated code that should be extracted:

- [ ] Similar code blocks across multiple files
- [ ] Repeated struct/data class transformations
- [ ] Duplicated validation logic
- [ ] Repeated error handling patterns
- [ ] Similar test setup code

**Action**: For each smell found, propose a specific fix or flag for discussion.

---

## Step 5: Run Security Scans

Execute security checks using Snyk:

```bash
pwd
```

Then run:

1. `snyk_sca_scan` - dependency vulnerabilities
2. `snyk_code_scan` - code security issues

### Manual Security Checklist

- [ ] No hardcoded secrets, tokens, or credentials
- [ ] Input validation on all external data
- [ ] No path traversal vulnerabilities
- [ ] Proper authentication/authorization checks
- [ ] Sensitive data not logged
- [ ] HTTPS/TLS used for external calls

**Action**: Fix security issues. If in test data, note but don't fix.

---

## Step 6: Review PR Feedback

If a GitHub PR exists for the current branch:

```bash
gh pr view --json number,reviews,comments,url 2>/dev/null
```

For each review comment:

1. **Categorize**: Bug | Enhancement | Style | Question | Blocker
2. **Assess**: Is this actionable? Does it require a decision?
3. **Prioritize**: Critical (must fix) | Should fix | Nice to have

---

## Step 7: Update Implementation Plan

Add verification findings to the implementation plan:

```markdown
## Verification Results

### Code Path Analysis

- [List traced paths and any issues found]

### Semantic Changes

- [List any behavioral changes detected]

### Code Smells

- [List smells found with proposed fixes]

### Security Findings

- [List security issues and resolutions]

### PR Feedback Items

- [List items requiring decisions]
```

---

## Step 9: Fix Issues (TDD Required)

**CRITICAL: ALL fixes MUST follow TDD. NEVER implement a fix without writing a failing test first.**

When verification identifies issues to fix:

1. Write a test that exposes the issue
2. Run the test - confirm it FAILS
3. Apply the minimum change to make the test pass
4. Run the test - confirm it PASSES
5. Run all test suites to verify no regressions

---

## Quick Reference

### Commands

| Task      | Command                                     |
| --------- | ------------------------------------------- |
| Check PR  | `gh pr view --json number,reviews,comments` |
| SCA scan  | `snyk_sca_scan` with absolute path          |
| Code scan | `snyk_code_scan` with absolute path         |
| Run tests | `./gradlew test`                            |

### Red Flags (Stop and Discuss)

- Breaking API changes without versioning
- Security vulnerabilities in non-test code
- Significant behavioral changes
- Unresolved PR blockers
- Significant code duplication (>20 lines copied)
