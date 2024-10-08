# Contribution guide (draft)

### Dependencies (macOS)
- install a JDK into the path specified in `.snyk.env.darwin`. Else the snyk scan may fail 

### Build and deploy

Build process happens through Gradle (as well as all dependency's connection). Managed in `build.gradle.kts` root file, parametrised by `gradle.properties` file.

Release happens automatically every Tuesday at 9am UTC. Also, could be run manually through GitHub's `Actions/Release` workflow run. Specified in `release.yml` file.

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
