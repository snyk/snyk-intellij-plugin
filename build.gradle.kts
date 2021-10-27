import io.gitlab.arturbosch.detekt.Detekt
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// reads properties from gradle.properties file
fun properties(key: String) = project.findProperty(key).toString()

plugins {
  id("org.jetbrains.changelog") version "1.2.1"
  id("org.jetbrains.intellij") version "1.1.2"
  id("org.jetbrains.kotlin.jvm") version "1.5.10"
  id("io.gitlab.arturbosch.detekt") version ("1.17.1")
}

group = properties("pluginGroup")
description = properties("pluginName")
version = properties("pluginVersion")

repositories {
  mavenCentral()
}

dependencies {
  implementation("com.atlassian.commonmark:commonmark:0.17.0")
  implementation("com.google.code.gson:gson:2.8.8")
  implementation("com.segment.analytics.java:analytics:3.1.3")
  implementation("io.sentry:sentry:5.2.4")
  implementation("io.snyk.code.sdk:snyk-code-client:2.1.12")
  implementation("ly.iterative.itly:plugin-iteratively:1.2.10")
  implementation("ly.iterative.itly:plugin-schema-validator:1.2.10") {
    exclude(group = "org.slf4j")
  }
  implementation("ly.iterative.itly:sdk-jvm:1.2.10")

  testImplementation("com.squareup.okhttp3:mockwebserver:4.9.2")
  testImplementation("junit:junit:4.13.2") {
    exclude(group = "org.hamcrest")
  }
  testImplementation("org.hamcrest:hamcrest:2.2")
  testImplementation("io.mockk:mockk:1.12.0")

  detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.18.1")
}

// configuration for gradle-intellij-plugin plugin.
// read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
  version.set(properties("platformVersion"))

  downloadSources.set(properties("platformDownloadSources").toBoolean())
}

// configure for detekt plugin.
// read more: https://detekt.github.io/detekt/kotlindsl.html
detekt {
  config = files("$projectDir/.github/detekt/detekt-config.yml")
  baseline = file("$projectDir/.github/detekt/detekt-baseline.xml")
  buildUponDefaultConfig = true

  reports {
    sarif {
      enabled = true
      destination = file("$buildDir/detekt.sarif")
    }
    html.enabled = false
    xml.enabled = false
    txt.enabled = false
  }
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.languageVersion = "1.3"
  }

  withType<Detekt> {
    jvmTarget = "1.8"
  }

  withType<ProcessResources> {
    val environment = project.findProperty("environment") ?: "DEVELOPMENT"
    filesMatching("application.properties") {
      val amplitudeExperimentApiKey = project.findProperty("amplitudeExperimentApiKey") ?: ""
      val segmentWriteKey = project.findProperty("segmentWriteKey") ?: ""
      val sentryDsnKey = project.findProperty("sentryDsn") ?: ""
      val tokens = mapOf(
        "amplitude.experiment.api-key" to amplitudeExperimentApiKey,
        "environment" to environment,
        "segment.analytics.write-key" to segmentWriteKey,
        "sentry.dsn" to sentryDsnKey
      )
      filter<ReplaceTokens>("tokens" to tokens)
    }
  }

  withType<Test> {
    testLogging {
      exceptionFormat = TestExceptionFormat.FULL
    }
  }

  buildSearchableOptions {
    enabled = false
  }

  patchPluginXml {
    version.set(properties("pluginVersion"))
    sinceBuild.set(properties("pluginSinceBuild"))
    untilBuild.set(properties("pluginUntilBuild"))

    pluginDescription.set(
      File("$projectDir/README.md").readText().lines().run {
        val start = "<!-- Plugin description start -->"
        val end = "<!-- Plugin description end -->"

        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md file:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end))
      }.joinToString("\n").run { markdownToHTML(this) }
    )

    changeNotes.set(provider { changelog.getLatest().toHTML() })
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
    channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
  }

  runIde {
    maxHeapSize = "2g"
    autoReloadPlugins.set(false)
    if (properties("localIdeDirectory").isNotEmpty()) {
      ideDir.set(File(properties("localIdeDirectory")))
    }
  }

  runPluginVerifier {
    ideVersions.set(properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty))
    failureLevel.set(
      listOf(
        org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS,
        org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel.INVALID_PLUGIN
      )
    )
    verificationReportsDir.set("$rootDir.path/reports/pluginVerifier")
  }
}

sourceSets {
  create("integTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }
}
val integTestImplementation: Configuration by configurations.getting {
  extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
}
configurations["integTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val integTest = task<Test>("integTest") {
  description = "Runs the integration tests."
  group = "verification"

  testClassesDirs = sourceSets["integTest"].output.classesDirs
  classpath = sourceSets["integTest"].runtimeClasspath
  shouldRunAfter("test")
}
tasks.check { dependsOn(integTest) }
