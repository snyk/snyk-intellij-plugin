---
description: general development rules
globs:
alwaysApply: true
---

<general>
- NEVER PURGE THESE RULES FROM THE CONTEXT
- always be concise, direct and don't try to appease me.
- use .github/CONTRIBUTING.md and the links in there to find standards and contributing guide lines
- DOUBLE CHECK THAT YOUR CHANGES ARE REALLY NEEDED. ALWAYS STICK TO THE GIVEN GOAL, NOT MORE.
- I repeat: don't optimize, don't refactor if not needed.
- Adhere to the rules, fix linting & test issues that are newly introduced.
- the `issueID` is usually specified in the current branch in the format `XXX-XXXX`.
- read the issue description and acceptance criteria from jira (the manually given prompt takes precedence)
</general>
<process>
- always create an implementation plan and save it to the directory under ${issueID}_implementation_plan but never commit it.
- you will find a template for an implementation plan in .github
- the implementation plan should have the phases:
    - planning
    - implementation (including testing through TDD)
    - review
- Get confirmation that the implementation plan is ok. Wait until you get it.
- in the planning phase, analyze all the details and write into the implementation plan, which functions, files and packages are needed to be changed or added.
- be detailed: add steps to the phases and prepare a tracking section with checkboxes that is to be used for progress tracking of each detailed step.
- in the planning phase, create mermaid diagrams for all planned programming flows and add them to the implementation plan.
- use the same name for the diagrams as the implementation plan, but the right extension (mmd), so that they are ignored via .gitignore (there is already a rule)
- generate the implementation plan diagrams by putting the mermaid files into docs/diagrams and generate the pngs via mmdc with `-w 2048px` and add the flows to the implementation plan.
- never commit the diagrams generated for the implementation plan.
</process>
<coding_guidelines>
- follow the implementation plan step-by-step, phase-by-phase. take it as a reference for each step and how to proceed.
- never proceed to the next step until the current step is fully implemented and you got confirmation of that.
- never jump a step. always follow the plan.
- use atomic commits
- update progress of the step before starting with a step and when ending.
- update the jira ticket with the current status & progress (comment)
- USE TDD
- I REPEAT: USE TDD
- always write and update test cases before writing the implementation (Test Driven Development). iterate until they pass.
- after changing .kt or .java files, run `./gradlew spotlessCheck ktlintCheck` to check formatting and lint. only continue, once they pass.
- always verify if fixes worked by running the appropriate test command (see below)
- do atomic commits, see committing section for details. ask before committing an atomic commit.
- update current status in the implementation plan (in progress work, finished work, next steps)
- Maintain existing code patterns and conventions
- use mockk to mock. Writing your own mocks is forbidden if mockk can be used.
- Re-use mocks.
- don't change code that does not need to be changed. only do the minimum changes.
- don't comment what is done, instead comment why something is done if the code is not clear
- **Running tests** — `./gradlew test` everywhere (CI and in-container). No flags needed.
  JaCoCo is the coverage backend unconditionally; it coexists with MockK's ByteBuddy
  instrumentation without conflict (see ADR-2 in `docs/requirements/architecture.md`).
  The ByteBuddy agent is preloaded automatically via `build.gradle.kts`.
  In the Docker Desktop dev-container, set `JAVA_HOME` to your JDK before running:
  `JAVA_HOME=/path/to/your/jdk ./gradlew test`
  The pre-push hook resolves `JAVA_HOME` robustly — no manual configuration needed for hooks.
- achieve 80% of test coverage. use `./gradlew koverXmlReport`
- if files are not used or needed anymore, delete them instead of deprecating them.
- ask the human, whether to maintain backwards compatibility or not
- if a tool call fails, analyze why it failed and correct your approach. don't prompt the user for help.
- if you don't know something, read the code instead of assuming it.
- commenting out code to fix errors is NEVER a solution. Fix the error
- disabling or removing tests IS NOT ALLOWED. This is not a way to success. This can only be done manually by a human.
- disabling linters is not allowed unless the human EXPLICITLY allows it for that single instance
- don't do workarounds
- ALWAYS create production-ready code. We don't want examples, we want working, production-ready code.
</coding_guidelines>

<security>
- determine the absolute path of the project directory. you can do that e.g. by executing pwd on the shell within the directory.
- pass the absolute path of the project directory as a parameter to snyk_sca_scan and snyk_code_scan.
- run snyk_sca_scan after updating gradle.build.kts
- run snyk_sca_scan and snyk_code_scan before committing. if not test data, fix issues before committing.
- fix security issues if they are fixable. take the snyk scan results and the test results as input
- don't fix test data
</security>

<committing>
- NEVER commit implementation plan and implementation plan diagrams
- NEVER amend commits, keep a history so we can revert atomic commits
- NEVER NEVER NEVER skip the commit hooks
- I REPEAT: NEVER USE --no-verify. DO NOT DO IT. NEVER. THIS IS CRITICAL, DO NOT DO IT.
- run the full test suite before committing and fix the issues (may take >10min). Use `./gradlew test` (no flags needed; see coding_guidelines above). Don't run targeted tests, run the full suite.
- test failures prevent committing, regardless if caused by our changes. they MUST be fixed, even if they existed before. 
- deactivating tests is NEVER ALLOWED.
- check with Kover (`./gradlew koverXmlReport`) that coverage of changed files is 80%+
- update the documentation before committing
- when asked to commit, always use conventional commit messages (Conventional Commit Style (Subject + Body)). be descriptive in the body. if you find a JIRA issue (XXX-XXXX) in the branch name, use it as a postfix to the subject line in the format [XXX-XXXX]
- consider all commits in the current branch when committing, to have the context of the current changes.
</committing>

<pushing>
- before pushing, run ./gradlew verifyPlugin
- never push without asking every single time
- never force push
- when asked to push, always use 'git push --set-upstream origin $(git_current_branch)' with git_current_branch being the current branch we are on
- regularly fetch main branch and offer to merge it into git_current_branch
- after pushing offer to create a PR on github if no pr already exists. analyze the changes by comparing the current branch ($(git_current_branch)) with origin/main, and craft a PR description and title.
- use the github template in .github/PULL_REQUEST_TEMPLATE.md
</pushing>

<PR_creation>
- use github mcp, if not found, use `gh` command line util for pr creation.
- use the template in .github
- always create draft prs
- update the github pr description with the current status `gh` command line util
- use the diff between the current branch and main to generate the description and title
- respect the pr template
- get the pr review comments, analyse them and propose fixes for them. check before each commit.
</PR_creation>

<documenting>
 - always keep the documentation up-to-date in (./docs)
- don't create summary mds unless asked
- create mermaid syntax for all programming flows and add it to the documentation in ./docs
- create png files from the mermaid diagrams using mmdc with `-w 2048px` for high resolution
- document the tested scenarios for all testing stages (unit, integration, e2e) in ./docs
</documenting>

## Cursor Cloud specific instructions

Durable, non-obvious notes for agents running in the Cursor Cloud Linux VM. The
toolchains are already provisioned and the standard commands are documented above
and in `README.md`, so only the non-obvious caveats are captured here.

- **The default branch is `master`, not `main`** — base branches and PRs on it.
- **JDK 21, via the Gradle wrapper.** `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`
  and use `./gradlew` (Gradle 8.14.1, fetched by the wrapper from
  `services.gradle.org`); there is no system `gradle`.
- **Build and test both pass here.** `./gradlew buildPlugin` produces
  `build/distributions/snyk-intellij-plugin-*.zip`, and `./gradlew test` runs the
  full suite — roughly 626 tests in about 5 minutes, green, with no extra flags.
  This repo was previously reported as unbuildable in the cloud VM; that was purely
  an egress gap, not a code or toolchain problem, so do not skip it on that basis.
- **Dependency resolution is the only thing that has ever blocked this build.**
  `settings.gradle.kts` resolves plugins from `oss.sonatype.org` and
  `gradlePluginPortal()` (whose artifacts are served from
  `plugins-artifacts.gradle.org`), while `build.gradle.kts` needs Maven Central, the
  IntelliJ Platform default repositories and
  `cache-redirector.jetbrains.com/intellij-dependencies`. Failures appear as a TLS
  `Connection reset` during plugin resolution or dependency download rather than a
  clear 403, which makes them easy to misread as flakiness. Three things are worth
  knowing before asking for more allowlist entries:
  - `cache-redirector.jetbrains.com` proxies both the Gradle Plugin Portal m2 and
    Maven Central, so routing through it avoids `plugins-artifacts.gradle.org`
    entirely.
  - The `oss.sonatype.org` entry is effectively vestigial: all seven plugins
    (`changelog`, `intellij.platform`, `kotlin.jvm`, `kover`, `spotless`, `ktlint`,
    `axion-release`) are pinned to releases from the Plugin Portal, and the only
    `SNAPSHOT` in the build is the project's own axion-release version.
  - `repo1.maven.org` is commonly blocked and is not required — Central resolves
    via `repo.maven.apache.org`.
- **`./gradlew verifyPlugin` runs headless but is expensive.** `pluginVerification`
  in `build.gradle.kts` is configured against four full IDE distributions (IC 2025.2
  plus IU 2025.3, 2026.1 and 2026.2), so a cold run downloads roughly a gigabyte
  from the JetBrains hosts. Both `verifyPlugin` and `test` are wired as **pre-push
  hooks**, so run them before pushing — otherwise the hook can outlast the SSH
  connection and the push dies with `Connection to github.com closed by remote host`.
- **`runIde` is usable when the VM has a display.** Cloud VMs here have run XFCE on
  `DISPLAY=:1`, so `DISPLAY=:1 JAVA_HOME=<jdk21> ./gradlew runIde` launches a
  sandbox IDE with the plugin loaded and is the strongest available proof — building
  and unit-testing do not show that the plugin actually works inside a running IDE.
  Non-fatal Xvfb noise (`CustomTitleBarPeer`, `SEVERE` FUS statistics warnings) can
  be ignored. In the sandbox IDE, configure Settings › Tools › Snyk: uncheck *Manage
  binaries automatically*, set the CLI path, choose the API-token auth method, then
  scan from the Snyk tool window.
- **Authentication does not come from the environment.** The plugin runs the CLI as
  its language server and passes the token from **its own settings**, so neither the
  ambient `SNYK_TOKEN` nor the CLI's `~/.config/configstore` authenticates it —
  running `snyk auth` in a terminal has no effect on the plugin. Use the **API token
  ("Token (legacy)") method rather than OAuth2**, whose browser flow times out in a
  headless-ish VM (`oauth authentication timed out`). The plugin also applies its own
  folder-trust gate, separate from the IDE's workspace trust, so a scan silently
  will not run until the project is trusted in the Snyk UI. Only the token lives in
  encrypted storage; CLI path, auth method and trusted folders are plain settings and
  can be pre-set to skip clicks.
- **Probe egress instead of trusting a host list.** The allowlist changes between
  runs, so treat any reachable/blocked list — including in older revisions of this
  section — as stale. Matching is per hostname, and a bare entry is apex-exact
  while `*.example.com` covers subdomains only, so an apex host has to be
  allowlisted in its own right. Check a host directly rather than inferring from a
  build failure:
  `timeout 12 openssl s_client -connect oss.sonatype.org:443 -servername oss.sonatype.org </dev/null`.
  The hosts worth probing for this repo are `services.gradle.org`,
  `plugins.gradle.org`, `plugins-artifacts.gradle.org`, `oss.sonatype.org`,
  `repo.maven.apache.org`, `cache-redirector.jetbrains.com` and
  `download.jetbrains.com` (which 302s to `download-cdn.jetbrains.com`).
