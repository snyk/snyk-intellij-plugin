import org.jetbrains.changelog.closure
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.changelog") version "0.4.0"
  id("org.jetbrains.intellij") version "0.4.21"
  id("org.jetbrains.kotlin.jvm") version "1.3.72"
}

// variables from gradle.properties file
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project
val platformVersion: String by project
val localIdeDirectory: String by project

group = "io.snyk.intellij"
description = "Snyk Vulnerability Scanner"
version = pluginVersion

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib")

  implementation("com.google.code.gson:gson:2.8.6")
  implementation("com.atlassian.commonmark:commonmark:0.15.2")

  testImplementation("junit:junit:4.12")
  testImplementation("org.mockito:mockito-core:3.5.2")
}

intellij {
  version = platformVersion
}

repositories {
  jcenter()
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.languageVersion = "1.3"
  }

  buildSearchableOptions {
    enabled = false
  }

  patchPluginXml {
    version(pluginVersion)
    sinceBuild(pluginSinceBuild)
    untilBuild(pluginUntilBuild)

    pluginDescription(closure {
      File("./README.md").readText().lines().run {
        val start = "<!-- Plugin description start -->"
        val end = "<!-- Plugin description end -->"

        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md file:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end))
      }.joinToString("\n").run { markdownToHTML(this) }
    })
  }

  publishPlugin {
    channels(pluginVersion.split('-').getOrElse(1) { "default" }.split('.').first())
  }

  runIde {
    if (localIdeDirectory.isNotEmpty()) {
      ideDirectory(localIdeDirectory)
    }
  }
}
