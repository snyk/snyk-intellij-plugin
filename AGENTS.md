## Project Overview

Snyk Security (`snyk/snyk-intellij-plugin`) is a Kotlin/Java Gradle-based IntelliJ Platform plugin (JetBrains Marketplace listing "Snyk Vulnerability Scanner") that surfaces Snyk Open Source, Snyk Code, and Snyk IaC scan results in JetBrains IDEs. It bundles/downloads the Snyk CLI (which ships the Snyk Language Server) and talks to it over LSP4J via `snyk.common.lsp.LanguageServerWrapper` and `SnykLanguageClient`.

## Build & Development Commands

```bash
./gradlew build                                 # compile + assemble the plugin
./gradlew test                                  # unit/integration tests
./gradlew koverXmlReport                        # coverage report (build/reports/kover/report.xml), after test
./gradlew spotlessCheck                         # verify ktfmt (Google Style) formatting
./gradlew spotlessApply                         # auto-format before committing
./gradlew ktlintCheck                           # lint-only checks
./gradlew verifyPlugin                          # IntelliJ Plugin Verifier against target IDEs
./gradlew clean ktlintCheck spotlessCheck check # what CI runs
```

Tests use JUnit4 with IntelliJ's `BasePlatformTestCase`/testFramework fixtures and MockK for mocking; prefer real platform testing over mocks. In the Docker Desktop dev-container, set `JAVA_HOME` before running Gradle.

## Architecture

- `src/main/kotlin/snyk/common/lsp/` — LS integration: `LanguageServerWrapper.kt` (process lifecycle, LSP4J capabilities), `SnykLanguageClient.kt` (server notifications/commands), `RangeConverter.kt`, `ScanState.kt`; subpackages `commands/`, `hovers/`, `progress/`, `analytics/`, `settings/`.
- `src/main/kotlin/snyk/common/annotator/` — editor annotators per product: `SnykOSSAnnotator.kt`, `SnykCodeAnnotator.kt`, `SnykIaCAnnotator.kt`, `SnykSecretsAnnotator.kt`, plus `SnykLineMarkerProvider.kt`, `CodeActionIntention.kt`.
- `src/main/kotlin/io/snyk/plugin/ui/toolwindow/` — tool window: `SnykToolWindowFactory.kt`, `SnykToolWindowPanel.kt`, with `panels/` (e.g. `IssueDescriptionPanel.kt`, `SummaryPanel.kt`).
- `src/main/kotlin/io/snyk/plugin/ui/jcef/` — JCEF-backed HTML views/bridges (`TreeViewBridgeHandler.kt`, `ExecuteCommandBridge.kt`, `GenerateAIFixHandler.kt`).
- `src/main/kotlin/io/snyk/plugin/services/` — `SnykTaskQueueService.kt`, `SnykCliAuthenticationService.kt`, `SnykApplicationSettingsStateService.kt`, `CliAdapter.kt`, plus `download/` (`CliDownloader.kt`, `SnykCliDownloaderService.kt`).
- `src/main/kotlin/io/snyk/plugin/cli/` — CLI process/result types (`CliResult.kt`, `CliError.kt`, `ConsoleCommandRunner.kt`).
- `src/main/kotlin/io/snyk/plugin/settings/` and `ui/settings/` — settings configurable and settings UI panels.
- `src/main/kotlin/io/snyk/plugin/events/` — IntelliJ message-bus listener interfaces (`SnykScanListener`, `SnykSettingsListener`) for pub/sub across the plugin.

## Conventions

- Package roots split as `io.snyk.plugin.*` (plugin-side app services/UI) vs `snyk.*` (`snyk.common`, `snyk.common.lsp`, `snyk.trust`, `snyk.sdk` — protocol/domain logic), mirrored 1:1 in `src/test/kotlin`.
- Test classes suffixed `*Test.kt`; integration-style tests use `*IntegTest.kt`. Shared test helpers live alongside tests without the suffix (`TestUtils.kt`, `InMemoryFsRule.kt`).
- Mocking is done with MockK, not Mockito — favor the real IntelliJ Testing Framework (`BasePlatformTestCase`) over heavy mocking.
- Formatting is ktfmt Google Style enforced via Spotless (2-space indent, 120 max line length); ktlint is restricted to lint-only rules. Star imports are effectively disabled.
- Kover is configured to use JaCoCo as its coverage backend everywhere (not the native Kover agent), since JaCoCo coexists with MockK's ByteBuddy instrumentation without conflict — see ADR-2 in `docs/requirements/architecture.md`.

## Development Workflow

- Read the Jira issue description/acceptance criteria before starting non-trivial work; update the ticket with a progress comment as you go.
- Use TDD: write/update tests before implementation, iterate until green.
- For non-trivial work, write an implementation plan first (planning → implementation → review phases with a progress checklist) and get confirmation before starting; never commit the plan or its diagrams.
- Make the minimum change needed — don't refactor or optimize beyond the stated goal. Comment on *why*, not *what*.
- Use MockK for mocking and reuse existing mocks.
- This is not a library: delete unused files instead of deprecating them.
- After changing `.kt`/`.java` files, run `./gradlew spotlessCheck ktlintCheck`; run the full test suite (`./gradlew test`) and check coverage (`./gradlew koverXmlReport`, target 80%+ on changed code) before committing. Never disable a linter or a test to get past this — only a human may do that.
- Run Snyk SCA/Code scans against the project's absolute path before committing and after `build.gradle.kts` changes; fix real findings, don't touch test fixtures.
- Before pushing, run `./gradlew verifyPlugin`.
- Before each commit, check for and address feedback from the PR review bot (snyk-pr-review-bot) on any open PR.
- Never use `--no-verify` or otherwise skip commit hooks, and never amend commits. Use atomic, conventional-commit-style commits; if a Jira ID (`XXX-XXXX`) appears in the branch name, append it to the subject.
- Never push without asking first, and never force-push. Regularly fetch `main` and offer to merge it into the working branch.
- After pushing, offer to open a draft PR using `.github/pull_request_template.md` (or update the existing PR description) with a title/description generated from the diff against `main`.
- Keep `./docs` up to date; document tested scenarios and add Mermaid diagrams for new flows.
