![Snyk logo](https://snyk.io/style/asset/logo/snyk-print.svg)

***

Snyk helps you find, fix and monitor for known vulnerabilities in Node.js npm, Ruby and Java dependencies, both on an ad hoc basis and as part of your CI (Build) system.

The Snyk IntelliJ plugin is able to scan your maven-based
projects against the snyk CLI.

For this initial release, it requires you to first log in via
the [Snyk Command-Line Interface](https://snyk.io/docs/using-snyk)

## Installation


This plugin is published in the [IntelliJ Repository](https://plugins.jetbrains.com/plugin/10972-snyk-vulnerability-scanning)

Install via IntelliJ's built-in plugin browser. 

## Supported IntelliJ versions

The plugin requires IntelliJ 2017.3 or above.

Other Jetbrains IDEs, such as PyCharm or WebStorm are not _currently_ supported.

SBT, Gradle, and other build-systems are scheduled for a future release.

## Standalone UI development

This plugin internally uses HTML and templating via [Handlebars.java](https://github.com/jknack/handlebars.java)
to display much of its user-interface via the
[JavaFX WebView](https://docs.oracle.com/javase/8/javafx/embedded-browser-tutorial/overview.htm).

This means that it's possible to render the content via a standalone server in your favourite browser.

In this mode, changes to any web artifacts (html, css, handlebars templates, etc.)
become visible after a simple page refresh, allowing for a very fast prototype/debug
without needing to re-launch a fresh instance of IntelliJ each time.  Sample content
and a mock of IntelliJ's "darkula" colour scheme is provided for this purpose.

To use, run the command `gradle previewHtmlUi`

All assets that can be live-updated are in the `src/main/resources/WEB-INF` folder.

## Inline UI development

Owing to rendering differences in the embedded JavaFX `WebPane`, it's sometimes necessary to refine small tweaks inside the IDE when they show a different appearance from the standalone mode.

To avoid the painful compile/relaunch round-trip time, it's possible to specify a WEB-INF directory via the `snyk.plugin.webinf` environment variable.  In this mode, changes to templates will become available immediately on page reload, within in the IDE.

There's also an endpoint `http://localhost:<port>/debugForceNav?path=<path>` that will force the Web Pane to navigate to the specified path.  This is especially useful for testing interstitial pages such as `/scanning` that are typically only on screen for a short time.

