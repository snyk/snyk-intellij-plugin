# Snyk Security Changelog

## [2.8.9]
### Features
- Updated `OssBulkFileListener` to include `.snyk` in the list of supported build files.
- When a `.snyk` file changes, the OSS cache will be dropped and rescans should be triggered.

Related PRs:
- [Language Server PR #563](https://github.com/snyk/snyk-ls/pull/563)

## [2.8.8]

### Fixes
- change some of the colours used in the HTML panel so it's consistent with designs
- renders errors or Snyk Code and Snyk OpenSource failed scans through the Language Server
- update required protocol version to 12

## [2.8.7]
### Fixes
- fix issue counts when there are ignores and add some warnings about the Issue View Options
- fix AI fix counts when there are ignores

## [2.8.6]
### Fixed
- automatically migrate old-format endpoint to https://api.xxx.snyk.io endpoint and save it in settings. Also, add tooltip to custom endpoint field explaining the format.
- fix multi-file links in the DataFlow HTML panel

## [2.8.5]
### Fixed
- don't display balloon warnings if IaC error is ignored (e.g. no IaC files found)
- don't output amplitude errors as warning, only debug

## [2.8.4]
### Fixed
- don't use kotlin specific convenience function that may cause errors on non kotlin IDEs

## [2.8.3]

### Added
- Add folder path to report analytics request.
- Support for 2024.4
- Spend less time in the UI thread

## [2.8.2]

### Added
- Inserts the IDE specific scripting.
- Add information about the number of ignored and non-ignored vulnerabilities for consistent ignores.
- Hides the AI Fix panel and adds more custom styling for IntelliJ.
- Adds position line interaction.

## [2.8.1]

### Added
- Injects custom styling for the HTML panel used by Snyk Code for consistent ignores.

## [2.8.0]

### Added
- Serve Snyk Code functionality via language server. This enables auto-scanning on startup / save, code actions for Snyk Learn and, if enabled, Snyk Auto-Fix. The number of uploaded files is not shown anymore.

## [2.7.21]

### Fixed
- Append /v1 to the endpoint when necessary

## [2.7.20]
### Added
- (LS OSS) Starts rendering the Tree Nodes for OSS via the LS
- (LS OSS) Renders the Suggestion Panel for OSS via the LS

## [2.7.19]
### Added
- Refactors some files so they can be used by more than just Snyk Code
- De-duplicated code which will be used by all products loaded via the Language Server
- Registry flag for integrating Snyk OSS scans in JetBrains via the LS

## [2.7.18]
### Added
- Improved theming in the Code Issue Panel by applying IntelliJ theme colors dynamically to JCEF components. This ensures consistency of UI elements with the rest of the IDE.

### Fixed
- Don't add /v1 to all API calls through the Language Server
- Default to using the correct API for the custom endpoint.

## [2.7.17]
### Fixed
- Fixed problem in re-enablement of scan types when only one scan type was selected

### Added
- Use https://api.XXX.snyk.io/v1 and https://api.XXX.snykgov.io/v1 as endpoint URLs
- Allow selection of CLI release channels (stable/preview)

## [2.7.16]
### Added
- Implemented dynamic theme style changes for the Code Issue Panel via the Language Server. It adjusts CSS properties based on the current IDE theme settings to enhance visual consistency across different themes. See related PR: [snyk-ls#491](https://github.com/snyk/snyk-ls/pull/491).

## [2.7.15]
### Fixed
- Re-enable scan results when re-enabling different scan types
- (LS Preview) do not trigger scan on startup for Snyk Code multiple times

## [2.7.14]
### Added
- force download of compatible CLI on mismatched LS protocol versions
- bumped LS protocol version to ensure built-in LS in CLI has necessary commands for global ignores
- (LS Preview) trigger scan on startup if auto-scan is enabled in Settings

## [2.7.13]
### Added
- Render Snyk Code vulnerabilities using HTML served by the Language Server behind a feature flag.
- (LS Preview) added timeout to commands executed via code lenses
- Add interaction for navigating to the source file from the HTML Data Flow panel.
- Support for Jetbrains 2024.1 platform

### Fixed
- (LS Preview) don't navigate to source of selected code tree node after scan
- (LS Preview) support Windows drive letters in file paths

## [2.7.12]
### Added
- Mark ignored findings as ignored behind a feature flag.

### Fixed
- (LS Preview) fix progress handling for Snyk Code scans
- (LS Preview) fix multi-project scanning for Snyk Code
- (LS Preview) fix auto-scan newly opened project, and ask for trust if needed
- (LS Preview) fix CodeVision for opened files

## [2.7.11]
### Added
- Consistent ignores for Snyk Code behind a feature flag.
- Render ignores settings behind a feature flag.

## [2.7.10]
### Fixed
- (LS Preview) Fix content root handling for Snyk Code scans

## [2.7.9]
### Fixed
- fix: shortened plugin name to just Snyk Security
- (LS Preview) Fix long-running UI operation to run outside of UI thread
- Remove duplicated annotations in Snyk Code

## [2.7.8]
### Fixed
- (LS Preview) UI freezes and initialization errors caused by CodeVision and Code annotations
- (LS Preview) check trust for content root before triggering Snyk Code scans

## [2.7.7]
### Fixed
- (LS Preview) Snyk Code scans when having multiple projects open
- (LS Preview) do not hang on missing answers to message requests, timeout after 5s
- Provide language-specific annotators for Snyk Code issues

## [2.7.6]
### Fixed
- some code refactorings and code smells

## [2.7.5]
### Fixed
- bump deps
- remove remnants of false-positives

### Added
feat: integrate experimental option to get Snyk Code results from Language Server (pre-alpha) [IDE-134] by @bastiandoetsch in #474


## [2.7.4]
### Fixed
- move all clean-up tasks on project close to a background task and limit execution to 5s
- close down & re-initialize language server when new CLI file is activated
- change trust service to work on project content roots instead of project base dir

## [2.7.3]
### Fixed
-  only send analytics when connected to an MT US environment

## [2.7.2]
### Fixed
- manually downloaded binaries were causing problems initiating scans

## [2.7.1]
### Fixed
- only start up language server after CLI update (fixes lock error on Windows)
- only start up one instance of language server, manage projects via workspace folders

## [2.7.0]
### Added
- Snyk controller extension point

## [2.6.2]
### Fixed
- remove more intellij-specific code and configuration

## [2.6.1]
### Fixed
- don't use kotlin library on non-kotlin IDEs

## [2.6.0]
### Fixed
- fix threading and UI incompatibilities with 2023.3

## [2.5.8]
### Added
- support for IntelliJ 2023.3 platform

## [2.5.6]

### Fixed
- Update vulnerability in org.json dependency [high severity]

## [2.5.5]

### Fixed
- Exception occurring due to a dependency conflict
- Add a requestID to all Snyk Code requests for better error analysis

## [2.5.4]

### Added

- support for arm64 CLI on macOS
- support to configure base URL for CLI downloads
- display up to 70 characters of the filepath in the tree view

## [2.5.3]

### Fixed

- updated dependencies


## [2.5.2]

### Added

- Snyk Code support for on-prem solutions (Snyk Code Local Engine) 

## [2.5.1]

### Changed

- Disable analytics and error reporting for snykgov.io domain

## [2.5.0]

### Changed

- Fix compatibility issues; only support Jetbrains 2023.x for plugin versions > 2.5.0

## [2.4.63]

### Fixed

- don't populate `.dcignore` with default ignore patterns

## [2.4.62]

### Fixed

- remove error message after ignoring IaC issue

## [2.4.61]

### Fixed

- calculate relative paths in IaC ignore functionality differently to avoid issues with `...` showing up sometimes

## [2.4.60]

### Fixed

- validate endpoint addresses
- handle exceptions in settings better

## [2.4.59]

### Fixed

- Sanitize paths when creating Treenodes in Snyk Open Source

## [2.4.57]

### Fixed

- Network requests when proxy authentication not required.

## [2.4.56]

### Fixed

- normalize/clean Snyk Code endpoint URL

## [2.4.55]

### Added

- support for Jetbrains 2023.1 platform

## [2.4.53]

### Fixed

- IaC false positive warnings for YAML files

## [2.4.52]

### Fixed

- IaC false positive warnings

## [2.4.51]

### Added

- Support for authenticating proxy servers

## [2.4.49]

### Added

- Support for IntelliJ 2022.3.

### Fixed

- Feedback url.

## [2.4.48]
### Added

- Project trust feature.

## [2.4.47]

### Fixed

- Snyk Code issues not highlighted in the editor.

## [2.4.46]

### Fixed

- IaC scans producing errors when fail to parse IaC-like files.

## [2.4.45]

### Fixed

- allow Snyk Code to be enabled during first authentication from preference dialog

## [2.4.43]

### Fixed

- `--all-projects` enabled by default for Rider IDE

## [2.4.41]

### Changed

- Wording for sending crash reports and analytics to Snyk

## [2.4.40]

### Fixed

- Plugin does not work in Jetbrains IDEs that are delivered without Kotlin

## [2.4.39]

### Added

- option to disable automatic CLI downloads
- option to specify the file path of the Snyk CLI executable

## [2.4.37]

### Fixed

- Found Container vulnerabilities now grouped by ID (similar to OSS results);
- For Container images with no remediation/fix available issues count(grouped by severity) now is shown.
- Container multi-images (OSS multi-build-managers) scan with no auth now redirect to auth panel.

### Added

- In the result's tree, second level nodes(file/image) now have number of vulnerabilities/issues found in it.

## [2.4.36]

### Fixed
- Results (json) received from CLI are now sanitized for correctness.
- For mixed correct and failed-to-parse json results: successfully parsed elements will be shown alongside with errors.
- Container scan for no-images-found case now produce correct message and visuals.

### Added
- For tree with results `Expand All`/`Collapse All` together with `Expand All Child` (for selected node) actions added;

### Changed
- Naming for scan results in the tree and naming of result's count is corrected.

## [2.4.35]

### Fixed
- Results (json) received from CLI are now sanitized for correctness.
- For mixed correct and failed-to-parse json results: successfully parsed elements will be shown alongside with errors.
- Some minor UI renaming and alignments

### Added
- Navigation from the Editor (using QuickFix's functionality at annotations) to Snyk ToolWindow
(correspondent node in the Tree opened and Description shown) for:
  - Open Source scan results;
  - SnykCode scan results;
  - Infrastructure as a Code scan results;
  - Container scan results.

## [2.4.34]

### Fixed
- All failed scan results for artifacts(file, image) are now shown in the results(Tree) too (right after successful scan results).
- Open Source Scan results annotations are shown for Gradle(Groovy) dependencies.

### Added
- Navigation to the Editor for the Open Source Scan results and all failed artifact's scan results.

## [2.4.33]

### Fixed
- Unify names for Types of Scan (Snyk Products) in different places of UI
- Other minor UI improvements (including updated Severity icons)

### Changed
- Separation of **Filtering**(for results in the Tree) and **Enablement**(in the Settings)for:
  - Types of Scan (Snyk Products)
  - Severities.

## [2.4.32]

### Fixed

- tweak and update Snyk Code communication with server:
  * encode and compress POST/PUT requests
  * update fallbacks for supported extensions and configFiles
  * do not proceed (send) files if only configFiles presence

### Changed
- Show specific description when no IaC or OSS files found but corresponded scan performed

## [2.4.31]

### Fixed

- Empty (or cancelled request for) proxy credentials
- `IllegalStateException` when no `Document` found for invalid(obsolete) file
- For Snyk Code multi-file issues code snippet in some data flow steps was shown from wrong file
- Correctly show some messages on macOS: Terms and Condition message on Auth panel and Container specific message for tree node
- Make re-try attempts for most Snyk Code api calls if not succeed for any reason (except 401 - auth failed)

## [2.4.30]

### Fixed

- URL encode proxy credentials
- Honoring user's product selection on welcome screen before first ever scan
- Proper message when Code product is disabled due to unreachable org settings on backend
- Auth screen has priority to be shown in case of invalid/empty token
- Code scan endpoint url was internally corrupted after any Snyk settings change

## [2.4.29]

### Fixed

- AlreadyDisposedException when annotations async refreshed for disposed project
- `IllegalArgumentException: wrong column` for issue's description panel creation
- failed psiFile search for invalid virtualFile
- `InvocationTargetExceptions` by extracting caches to separate class

## [2.4.28]

### Changed
- Enable HTTP request logging when Snyk Code logger in debug mode

### Fixed
- generic .dcignore file creation failure and exceptions

## [2.4.27]

### Changed
- Snyk Code: add support for Single Tenant setups
- Update organization setting tooltip text to clarify expected value.

### Fixed
- Avoiding IllegalArgumentException and IndexOutOfBoundsException due to accessing out-of-bound offset.

## [2.4.26]

### Changed

- Updated Snyk Code API to 2.3.1 (limit file size to 1 MB)
- Provide link to [Privacy Policy](https://snyk.io/policies/privacy/) and [Terms of Service](https://snyk.io/policies/terms-of-service/) on Welcome screen.
### Fixed

- Avoiding IncorrectOperationException by remove direct project service's requests.
- Avoid ClassNotFoundException due to not bundled Json support in some IDE.
- Fix possible duplicates and missing annotations for Snyk Code

## [2.4.25]

### Fixed

- Sanitise TextRange in file before accessing it.
- Request re-authentication from Snyk Code scan if token found to be invalid.

## [2.4.24]

### Fixed
- Disable force [auto-save in annotators](https://github.com/snyk/snyk-intellij-plugin/issues/324)

## [2.4.23]

### Fixed
- Snyk Code: make analysis retrieval more resilient to server/net errors.
- Snyk Code: add analysis context to improve analysis retrieval.
- Usage of kotlin-plugin specific method removed (cause `ClassNotFoundException: org.jetbrains.kotlin.psi.psiUtil`)

## [2.4.22]

### Changed
- Provide an additional info to the “Organization” setting

### Fixed

- Split caches update to be performed independently per product to avoid cross-affection if any failed.
- Container scan now extract images from Helm generated k8s yaml with image names inside quotes.
- Container scan should distinct image names when calling CLI.
- Container scan should add annotations for all duplicated images.
- Container scan should correctly proceed images with registry hostname in the name.
- Container scan should extract images with `port` and `tag` as well as `digest` in the path.
- Avoiding errors due to VirtualFile validity check while PsiFile obtaining.

## [2.4.21]

## [2.4.20]

### Changed
- Check Snyk Code enablement using configured organization from settings.
- Container specific messages when Container root tree node is selected.

### Fixed

- avoid any IDE internal InvocationTargetException for project services
- Container results/images cache was not updated on full/partial file content deletion.

## [2.4.19]

### Changed

- Renamed plugin to `Snyk Security - Code, Open Source, Container, IaC Configurations`
- Disable Snyk Code upload when Local Code Engine is enabled

### Fixed

- Snyk Vulnerability Database issue url resulting in 404 when opened from details panel.

## [2.4.18]

### Changed

- Snyk Open Source: added editor annotations for Maven, NPM, and Kotlin Gradle
- Snyk Open Source: added quickfix capability for package managers
- Snyk Code: Annotations if plugins for the language are installed
- Add amplitude events to quickfix display and invocations

### Fixed

- Fix Container: invalid token shows error and does not redirect to Auth panel
- Fix Container: should handle case if no images in project found
- Fix Container: node still showing last results even if disabled
- Improved Snyk Container image parsing in K8S files

## [2.4.17]

### Changed

- 2022.2 EAP support

### Fixed

- Fix IndexOutOfBoundsException during line number requests

## [2.4.16]

### Fixed

- Fix exception in IaC and Container annotators

## [2.4.15]

### Fixed

- Recognition of k8s YAML files started with `---`
- Caret is wrongly jumping to the last selected line in the Tree when editing file with issues

## [2.4.14]

### Changed

- Make Snyk Container generally available
- Make Snyk Infrastructure as Code generally available

## [2.4.13]

### Changed

- Use IntelliJ http(s) proxy settings for all remote connections
- Ignoring issues in Snyk IaC now ignores the instance of the issue instead of all instances of the issue type.

### Fixed

- Caches update and cleaning
- InvocationTargetException on project-less authentication
- Memory leak in Authentication panel

## [2.4.12]

### Fixed

- align IaC description panel inner indentations

### Changed

- needs new Snyk CLI executable for ignore functionality
- ignore issue now provides path to CLI instead of ignoring an issue project-wide

## [2.4.11]

### Changed

- enable new onboarding workflow for all users
- feedback link with new address

### Fixed

- don't delete CLI before download has finished successfully
- don't show balloon notifications if a Snyk product does not find supported files
- UI fixes for detail panel in Snyk view
- recreate API client on token change for Snyk Advisor
- several other bug fixes

## [2.4.10]

### Fixed

- avoid AlreadyDisposedException due to Project disposal before using project's service
- ignore exceptions in PsiManager.findFile()

## [2.4.9]

### Changed

- updated README with Log4Shell detection note

## [2.4.8]

### Fixed

- don't show an error, when no supported package manager was found for Snyk OSS
- use Snyk Code API 2.2.1, gives support to automatically handling empty files
- updated dependencies

## [2.4.7]

### Changed

- use new Snyk Code API
- remove scan reminder from experiment

## [2.4.6]

### Changed

- new tool window icon
- new experimental welcome workflow

## [2.4.5]

### Fixed

- run CLI download retries in background instead of UI thread
- validate CLI download with sha-256
- allow Snyk Code scan for multi-modules project (cause IllegalStateException before)

### Changed

- feedback link and message update

## [2.4.4]

### Fixed

- Handle errors when downloading Snyk CLI

### Changed

- Send missing analytic events by plugin install/uninstall
- Rename IssueIsViewed analytic event to IssueInTreeIsClicked
- Show links (Introduced through) only for NPM packages
- Create Sentry release on plugin release to be able to assign bugs to be fixed in next release
- Download Snyk CLI from static.snyk.io instead of GitHub releases
- Use TLS 1.2 as default to communicate with Snyk API

## [2.4.3]

### Fixed

- Show correct result in the tree when Snyk Code scan was stopped by user
- Send analytic events correctly when triggering Snyk Code analysis

### Changed

- Increase timeout for Snyk Code scan to 12 minutes. Configurable via 'snyk.timeout.results.waiting' registry key.
- Make plugin compatible with 2021.3 version

## [2.4.2]

### Changed

- Require restarting the IDE when updating or uninstalling the
  plugin ([GH-182](https://github.com/snyk/snyk-intellij-plugin/issues/182))

### Fixed

- Remove IntelliJ API methods that are scheduled for removal in 2021.3

## [2.4.1]

### Fixed

- Fix ClassCastException when updating the plugin without rebooting IDE

## [2.4.0]

### Added

- Allow submitting error reports anonymously to Sentry when exceptions occur

### Changed

- Increase performance when collecting files for Snyk Code

### Fixed

- Fix exception when Advisor analyse in-memory files ([GH-161](https://github.com/snyk/snyk-intellij-plugin/issues/161))
- Fix wrong cache invalidation for OSS results when modifying non-manifest files

## [2.3.0]

### Added

- Add Advisor support for Python packages in requirements.txt
- Show welcome notification after first plugin install
- Run `runPluginVerifier` task as part of CI workflow

### Changed

- Remove logo image from product selection panel
- Update Iteratively tracking plan for Advisor events

### Fixed

- Fix exception by downloading CLI when no GitHub release info available
- Fix error when initializing Iteratively library

## [2.2.2]

### Added

- Download CLI if needed when starting scans

### Fixed

- Fix exception by empty CLI output ([GH-153](https://github.com/snyk/snyk-intellij-plugin/issues/153))
- Fix error when parsing malformed JSON produced by CLI
- Disable `Run scan` action during `CLI download` task
- Fix cancelling action for `CLI download` task
- Fix issue with partially downloaded CLI file

## [2.2.1]

### Changed

- Make plugin compatible with 2021.2 version

## [2.2.0]

### Added

- [Advisor](https://snyk.io/advisor) support for NPM packages in package.json

## [2.1.9]

### Fixed

- Fix ProcessNotCreatedException when running OSS scans and CLI is still downloading
- Allow authentication while CLI is still downloading

## [2.1.8]

### Added

- Notify user in case of network/connection problem
- Welcome(onboarding) screen simplification by removing interactive alerts
- User feedback request
- Display auth link for possibility of manual open/copy into browser

### Changed

- Reduce network attempts to ask for SAST feature

### Fixed

- Exception(SunCertPathBuilderException) in case of custom/invalid certificate usage
- Welcome and Auth screen was missed to display in some cases
- Run action availability before auth passed

## [2.1.7]

### Fixed

- Set maximal attempts for asking for SAST feature

## [2.1.6]

### Fixed

- Fix [Exception when Settings panel invoked](https://github.com/snyk/snyk-intellij-plugin/issues/121)

## [2.1.5]

### Changed

- Check if Snyk Code (SAST) enabled for organisation on server side

### Fixed

- Consider `ignoreUnknownCA` option for all external network calls
- Fix Retrofit creation Exception for invalid endpoint

## [2.1.4]

### Changed

- Support critical severity level

### Fixed

- Consider `ignoreUnknownCA` option for Snyk Code
- Fix an error when displaying license issues

## [2.1.3]

### Changed

- Show OSS results as `obsolete` when it's outdated
- Fix errors on Project close while scan is running
- Fix and improve .dcignore and .gitignore parsing and usage
- Bugfix for `missing file parameter` exception

## [2.1.2]

### Changed

- Make plugin compatible with 2021.1 version

## [2.1.1]

### Changed

- Update plugin description marketplace page

## [2.1.0]

### Added

- Integrate Snyk Code product
- Simplify authentication process
- Add results filtering in Snyk tool window
- Add results caching to improve UI experience

### Changed

- Rework and add missing icons in the tree component

### Fixed

- Remove duplicated vulnerability reporting in the tree component

## [2.0.4]

### Fixed

- Fix handling custom endpoint option

## [2.0.3]

### Added

- Allow specifying CLI working directory for project submodules
- Improve integration names between different JetBrains IDEs

### Changed

- Change since/until build to `202-203.*`

### Fixed

- Split CLI additional arguments correctly

## [2.0.2]

### Added

- Add icons for supported package managers

## [2.0.1]

### Added

- Propagate integration name for JetBrain IDEs

### Fixed

- Hide API token in configuration settings

## [2.0.0]

### Added

- Use IntelliJ tree component to display vulnerabilities
- Use GitHub Actions for build and release process

### Fixed

- Remove JavaFX component for displaying vulnerabilities
- Remove Travis CI integration
