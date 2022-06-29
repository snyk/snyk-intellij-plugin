# Contribution guide (draft)

__As of state for June 2022__

### Architectural Overview
There are two projects' packages structures in source tree right now:
- `io.snyk.plugin` - Old one (legacy). Grouping classes/components by it's IntelliJ framework role, i.e. `.events`, `.services`, etc. mixed with our own components groups: `.net`, `.cli`, etc.
- `snyk` - New one. Grouping by Snyk Product (`.advisor`, `.iac`, etc) or service (`.common`, `.net`, etc)

**todo:** Unify project packages structure to either `io.snyk.plugin` or `snyk`

Ideally all communication between components/services should happen through events' mechanism (see `SnykScanListener` and it's usages/implementations as example). 

**todo:** But that's not always true presently. :( 

All communication through network should go through `HttpClient`/`ApiClient` from `.net` package to reuse Client whenever possible (mostly to reuse Interceptors, SSL Certificate handlers, timeouts, etc.) 

**todo:** Unify `.net` packages from `io.snyk.plugin` and `snyk` roots

Communication with CLI goes through `CliAdapter` (with `ConsoleCommandRunner` call under the hood): i.e. you need to inherit from it for every specific CLI usage/call (see `OssService` as example)

None-CLI product (SnykCode) communicate directly to Code backend using `java-code-sdk` library. Implementation of required classes are inside `io.snyk.plugin.snykcode` package

**todo:** Unify `.snykcode` network related classes with `.net` package classes.

`ProductType` holds textual representation for each supported product and should be the source of truth and the only place for such texts. 

All cached scan results (except SnykCode?) should be holding in `SnykCachedResults` service and requested/updated there when needed.

All project-files-on-disk update (changed, deleted, created, moved, copied) events happened through correspondent implementations of `SnykBulkFileListener` class (see existing implementations for examples of using)

All (most) of the setup needed to be done on plugin start or new Project opening happened at `SnykPostStartupActivity`

All application-wide persisted settings should be held in `SnykApplicationSettingsStateService` and Project-wide settings at correspondent `SnykProjectSettingsStateService`

Any new scan/re-scan should be executed through `SnykTaskQueueService`

Scan related options/settings are held inside `SnykApplicationSettingsStateService` while for the representation of scan results in the Tree we have also _filters_ (by Severity or Product) which should operate on top of the _settings_.

For UI (especially on top level) we're trying to use Idea built-in panels and factory classes/methods like `SimpleToolWindowPanel` or `JPanel`/`JBPanel` with `BorderLayout` as it seems to be more "native" for Idea. For more complicated custom panels we use `JPanel` with `GridConstraints` (see `io.snyk.plugin.ui.UIUtils` for the clue) 

Beware of needed wrapping of any UI related code inside of `ApplicationManager.getApplication().invokeLater{}` if you call it from any background thread. 

All our instances of Idea's `@Service` classes should be called through correspondent wrapper `get<xxx>Service()` in `io.snyk.plugin.Utils` as some checks need to be done (especially for project's services). Also, Jetbrains changed already in the past recommended way to invoke such services, so would be nice to have such invocations centralised for possible future changes.

Before creating PR (if any user related changes been made) please don't forget to update CHANGELOG.md (follow existing structure) to be reflected in released plugin's Change Notes.

### Build and deploy

Build process happened through Gradle (as well as all dependency's connection). Managed in `build.gradle.kts` root file, parametrised by `gradle.properties` file.

Release happened automatically every Tuesday at 9am UTC. Also, could be run manually through GitHub's `Actions/Release` workflow run. Specified in `release.yml` file.

### Testing
Should be mostly done trough [IntelliJ Platform Testing Framework](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html) and placed into `integTest` source root except simple independent Unit tests (`test` source root).

Mocks are [not recommended](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html#mocks) by Jetbrains, but we heavily( and successfully?) use Mockk framework. Just be careful not to mock whole world and make sure you're testing real functionality and not mocked one.

### Useful Links:
- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij) - that's your "Holy book" :)
- [Gradle IntelliJ Plugin](https://github.com/JetBrains/gradle-intellij-plugin) - needed for plugin development. See it's
[usage documentations](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html)
- [IntelliJ Platform Explorer](https://plugins.jetbrains.com/intellij-platform-explorer) - here you can find examples of any(?) Extension Point usage.
Imho better look into IntelliJ Idea sources for implementation.
- [Forum/FAQ](https://intellij-support.jetbrains.com/hc/en-us/community/topics/200366979-IntelliJ-IDEA-Open-API-and-Plugin-Development)  for IntelliJ IDEA Open API and Plugin Development
- [Slack channel](https://jetbrains-platform.slack.com/archives/C5U8BM1MK) for Plugin development for IntelliJ Platform
- [IntelliJ Platform UI Guidelines](https://jetbrains.github.io/ui/)
- [Icons](https://jetbrains.github.io/ui/resources/icons_list/) search in IntelliJ platform built-in list
