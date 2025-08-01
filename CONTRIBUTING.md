# Contributing to the Snyk IDE Extensions

We welcome contributions, but please read first! To ensure a smooth process and that your valuable work aligns with our roadmap, please keep the following in mind to help manage expectations:

## 1. Planning your changes

Before undertaking any changes or new features, please discuss your plans with us. This helps align on scope, design, technical approach, and priority.  
Even bug fixes can have unforeseen impacts or alternative solutions better suited for the codebase, so please ask first, we will be happy to discuss.  
Please raise a request with [support](https://support.snyk.io). (Snyk employees, use `#ask-ide`)

## 2. Where changes should be made

Consider whether your proposed change should be implemented within the IDE extension(s) or in the shared Language Server and related stack.
- [Snyk Language Server](https://github.com/snyk/snyk-ls)
- [Go Application Framework](https://github.com/snyk/go-application-framework)
- [Code Client Go](https://github.com/snyk/code-client-go)

## 3. Cross-IDE consistency

If your change is applicable to other Snyk IDE plugins as well, we may expect you to submit similar PRs for the other relevant IDE repositories after your initial PR has been reviewed and approved, as they will _usually_ need to be merged all at once or not at all.
- [Snyk VSCode extension](https://github.com/snyk/vscode-extension)
- [Snyk Eclipse plugin](https://github.com/snyk/snyk-eclipse-plugin)
- [Snyk Visual Studio extension](https://github.com/snyk/snyk-visual-studio-plugin)

## 4. Manual testing

All changes must be thoroughly manually tested by you.  
For visual changes the PR template asks for screenshots, so this is a good opportunity to snap them.

## 5. Documentation changes

Any user-facing changes will require [documentation](https://docs.snyk.io/) changes, which you will need to prepare.
If you do not have access to our content management system (you are not a Snyk employee), please add the documentation changes required (including new wording and screenshots) to the PR description.

We can instruct you on what to add to the CHANGELOG.md, so please ask.

---

# Making changes (draft)

### Dependencies (macOS)
- install a JDK into the path specified in `.snyk.env.darwin`. Else the snyk scan may fail 

### Build and deploy

Build process happens through Gradle (as well as all dependency's connection). Managed in `build.gradle.kts` root file, parametrised by `gradle.properties` file.

### Testing
Should be mostly done trough [IntelliJ Platform Testing Framework](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html) and placed into `integTest` source root except simple independent Unit tests (`test` source root).

Mocks are [not recommended](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html#mocks) by Jetbrains, but we heavily (and successfully) use Mockk framework. Just be careful not to mock whole world and make sure you're testing real functionality and not mocked one.

### Running the extension

- From the toolbar click `Run` -> `Run`
- Click `Edit Configuration` -> `Add new configuration`
- Select `Gradle` from the configuration list
- Type `runIde` in the `Run` textbox to select the `runIde` run command
- Click `Apply` and `Run` to run the extension`

### Target specific IDE distribution
If you want to run the plugin in other IDE distribution (e.g. Rider), you should pass set IDE `Contents` directory as a `localIdeDirectory` property in `gradle.properties`.

Here's an example for local Rider installation:
`localIdeDirectory=/Users/michel/Library/Application Support/JetBrains/Toolbox/apps/Rider/ch-0/221.5787.36/Rider.app/Contents`

You can copy the full path from IDE Settings in the JetBrains Toolbox.

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
