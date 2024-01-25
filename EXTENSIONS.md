# Snyk Extensions

This plugin offers an extension point for integrating Snyk with other Jetbrains IDE Plugins.

## What is the Snyk Controller?

The Snyk Controller is, put simply, a way to control Snyk from other plugins.

## Why would I use the Snyk Controller extension?

There are a few of use cases for the Snyk controller:

### Initiating a Snyk scan

There may be situations in which a plugin wants to initiate a security scan, especially at the end of a workflow which
introduces changes to the project source code, manifest dependencies, OCI container builds, infrastructure files,
etc. -- anything Snyk can scan.

### Determining whether Snyk is authenticated to a user

It might be useful to know whether the user has authenticated with Snyk; is Snyk ready to run scans?

## How do I use the extension in my plugin?

### Add a dependency on this plugin to your plugin's `plugin.xml` file.

Release >= 2.7.0 provides the extension point.

```xml
<depends>io.snyk.snyk-intellij-plugin</depends>
```

### Declare the extension

Add an extension for `io.snyk.snyk-intellij-plugin.controllerManager` to your plugin's `plugin.xml` file.

```xml
<extensions defaultExtensionNs="io.snyk.snyk-intellij-plugin">
  <controllerManager implementation="com.example.demointellij.MySnykControllerManager"/>
</extensions>
```

### Optional dependency

The dependency on Snyk can also be optional:

```xml
<depends optional="true" config-file="optional/withSnyk.xml">io.snyk.snyk-intellij-plugin</depends>
```

Declare the controller extension in the dependency's config file, located relative to the `plugin.xml`:

```xml
<!-- Contents of optional/withSnyk.xml -->
<idea-plugin>
    <extensions defaultExtensionNs="io.snyk.snyk-intellij-plugin">
        <controllerManager implementation="com.example.demointellij.HelloWorldControllerManager" />
    </extensions>
</idea-plugin>
```

### Implement the controller manager interface

The manager will be instantiated and passed an instance of the controller when the Snyk plugin is initialized.

```kotlin
package com.example.demointellij

import io.snyk.plugin.extensions.SnykController
import io.snyk.plugin.extensions.SnykControllerManager

class HelloWorldControllerManager : SnykControllerManager {
  override fun register(controller: SnykController) {
    Utils().getSnykControllerService().setController(controller)
  }
}
```

Snyk recommends building a [service](https://plugins.jetbrains.com/docs/intellij/plugin-services.html) to receive the
controller instance and provide it to other parts of your plugin.

### Use the controller in your plugin

See [SnykController](https://github.com/snyk/snyk-intellij-plugin/blob/main/src/main/kotlin/io/snyk/plugin/extensions/SnykController.kt)
for the current Snyk methods supported.

#### Initiating a scan

```kotlin
Utils().getSnykControllerService().getController()?.scan()
```

## What compatibility guarantees are there, for consumers of this extension?

Our [semantic version](https://semver.org/) releases indicate the compatibility of the extension API with respect to
past releases.

With a minor version increment, new methods may be added to interfaces, but existing methods will not be removed or
their prototypes changed.

With a major version increment, there are no compatibility guarantees. Snyk suggests checking the release notes, source
changes, and compatibility testing before upgrading.
