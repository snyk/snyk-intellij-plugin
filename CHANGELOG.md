# Snyk Vulnerability Scanner Changelog

## [2.1.3]
### Changed
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
