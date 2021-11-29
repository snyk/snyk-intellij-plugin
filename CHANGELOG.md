# Snyk Vulnerability Scanner Changelog

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
- allow Snyk Code scan for multi-modules project (cause IlligalStateException before)
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
- Require restarting the IDE when updating or uninstalling the plugin ([GH-182](https://github.com/snyk/snyk-intellij-plugin/issues/182))
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
