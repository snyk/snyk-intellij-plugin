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

- `src/main/kotlin/snyk/common/lsp/` handles LS integration: `LanguageServerWrapper.kt` (process lifecycle, LSP4J capabilities), `SnykLanguageClient.kt` (server notifications/commands), `RangeConverter.kt`, `ScanState.kt`, plus subpackages `commands/`, `hovers/`, `progress/`, `analytics/`, `settings/`.
- `src/main/kotlin/snyk/common/annotator/` holds editor annotators per product: `SnykOSSAnnotator.kt`, `SnykCodeAnnotator.kt`, `SnykIaCAnnotator.kt`, `SnykSecretsAnnotator.kt`, plus `SnykLineMarkerProvider.kt`, `CodeActionIntention.kt`.
- `src/main/kotlin/io/snyk/plugin/ui/toolwindow/` is the tool window: `SnykToolWindowFactory.kt`, `SnykToolWindowPanel.kt`, with `panels/` (e.g. `IssueDescriptionPanel.kt`, `SummaryPanel.kt`).
- `src/main/kotlin/io/snyk/plugin/ui/jcef/` holds JCEF-backed HTML views/bridges (`TreeViewBridgeHandler.kt`, `ExecuteCommandBridge.kt`, `GenerateAIFixHandler.kt`).
- `src/main/kotlin/io/snyk/plugin/services/` has `SnykTaskQueueService.kt`, `SnykCliAuthenticationService.kt`, `SnykApplicationSettingsStateService.kt`, `CliAdapter.kt`, plus `download/` (`CliDownloader.kt`, `SnykCliDownloaderService.kt`).
- `src/main/kotlin/io/snyk/plugin/cli/` holds CLI process/result types (`CliResult.kt`, `CliError.kt`, `ConsoleCommandRunner.kt`).
- `src/main/kotlin/io/snyk/plugin/settings/` and `ui/settings/` hold the settings configurable and settings UI panels.
- `src/main/kotlin/io/snyk/plugin/events/` has the IntelliJ message-bus listener interfaces (`SnykScanListener`, `SnykSettingsListener`) for pub/sub across the plugin.

## Conventions

- Package roots split as `io.snyk.plugin.*` (plugin-side app services/UI) vs `snyk.*` (`snyk.common`, `snyk.common.lsp`, `snyk.trust`, `snyk.sdk`, i.e. protocol/domain logic), mirrored 1:1 in `src/test/kotlin`.
- Test classes suffixed `*Test.kt`; integration-style tests use `*IntegTest.kt`. Shared test helpers live alongside tests without the suffix (`TestUtils.kt`, `InMemoryFsRule.kt`).
- Mocking is done with MockK, not Mockito. Favor the real IntelliJ Testing Framework (`BasePlatformTestCase`) over heavy mocking.
- Formatting is ktfmt Google Style enforced via Spotless (2-space indent, 120 max line length); ktlint is restricted to lint-only rules. Star imports are effectively disabled.
- Kover is configured to use JaCoCo as its coverage backend everywhere (not the native Kover agent), since JaCoCo coexists with MockK's ByteBuddy instrumentation without conflict. See ADR-2 in `docs/requirements/architecture.md`.

## Development Workflow

- Read the Jira issue description/acceptance criteria before starting non-trivial work; update the ticket with a progress comment as you go.
- Never commit an implementation plan or its diagrams to the repo.
- Use MockK for mocking and reuse existing mocks.
- This is not a library: delete unused files instead of deprecating them.
- After changing `.kt`/`.java` files, run `./gradlew spotlessCheck ktlintCheck`; run the full test suite (`./gradlew test`) and check coverage (`./gradlew koverXmlReport`, target 80%+ on changed code) before committing.
- Run Snyk SCA/Code scans against the project's absolute path before committing and after `build.gradle.kts` changes; fix real findings, don't touch test fixtures.
- Before pushing, run `./gradlew verifyPlugin`.
- Before each commit, check for and address feedback from the PR review bot (snyk-pr-review-bot) on any open PR.
- Never use `--no-verify` or otherwise skip commit hooks, and never amend commits. Use atomic, conventional-commit-style commits; if a Jira ID (`XXX-XXXX`) appears in the branch name, append it to the subject.
- Never push without asking first, and never force-push. Regularly fetch `main` and offer to merge it into the working branch.
- After pushing, offer to open a draft PR using `.github/pull_request_template.md` (or update the existing PR description) with a title/description generated from the diff against `main`.
- Keep `./docs` up to date; document tested scenarios and add Mermaid diagrams for new flows.

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
  full suite green in a few minutes. This repo was previously reported as unbuildable
  in the cloud VM; that was purely an egress gap, not a code or toolchain problem, so
  do not skip it on that basis.
- **A cached `test` task exits 0 without running anything.** On a warm VM Gradle can
  report `test` as `UP-TO-DATE` and succeed in seconds having executed no tests, which
  reads as a pass and is not one. When the point is to *prove* the suite is green
  rather than to iterate, run `./gradlew test --rerun-tasks` and check that the
  reported test count is non-zero.
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
  folder-trust gate, separate from the IDE's workspace trust, so a scan will not run
  silently until the project is trusted in the Snyk UI. The token, CLI path, auth
  method and trusted folders are all plain settings serialized to `snyk.settings.xml`
  today (`SnykApplicationSettingsStateService.kt` has an open TODO to migrate the
  token to IntelliJ's secure-storage API) — none of it is encrypted at rest yet, so
  treat that XML file (and any full settings export) as sensitive. All can be
  pre-set to skip clicks.
- **Probe egress instead of trusting a host list.** The allowlist only changes when
  someone asks the admins to change it, but a list written into a document drifts from
  it silently, so treat any reachable/blocked list — including in older revisions of
  this section — as unverified. Matching is per hostname, and a bare entry is apex-exact
  while `*.example.com` covers subdomains only, so an apex host has to be
  allowlisted in its own right. Check a host directly rather than inferring from a
  build failure:
  `timeout 12 openssl s_client -connect oss.sonatype.org:443 -servername oss.sonatype.org </dev/null`.
  The hosts worth probing for this repo are `services.gradle.org`,
  `plugins.gradle.org`, `plugins-artifacts.gradle.org`, `oss.sonatype.org`,
  `repo.maven.apache.org`, `cache-redirector.jetbrains.com` and
  `download.jetbrains.com` (which 302s to `download-cdn.jetbrains.com`).
