import io.gitlab.arturbosch.detekt.Detekt
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// reads properties from gradle.properties file
fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("org.jetbrains.changelog") version "2.1.2"
    id("org.jetbrains.intellij") version "1.16.1"
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("io.gitlab.arturbosch.detekt") version ("1.23.4")
    id("pl.allegro.tech.build.axion-release") version "1.13.6"
}

version = scmVersion.version

group = properties("pluginGroup")
description = properties("pluginName")

val jdk = "17"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")

    implementation("org.commonmark:commonmark:0.21.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.segment.analytics.java:analytics:3.4.0")
    implementation("io.sentry:sentry:6.27.0")
    implementation("javax.xml.bind:jaxb-api:2.3.1") // necessary because since JDK 9 not included
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.7.1")
    implementation("org.json:json:20231013")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("ly.iterative.itly:plugin-iteratively:1.2.11") {
        exclude(group = "com.fasterxml.jackson.core")
    }
    implementation("ly.iterative.itly:plugin-schema-validator:1.2.11") {
        exclude(group = "org.slf4j")
    }
    implementation("ly.iterative.itly:sdk-jvm:1.2.11") {
        exclude(group = "org.json")
    }

    testImplementation("com.google.jimfs:jimfs:1.3.0")
    testImplementation("com.squareup.okhttp3:mockwebserver")

    testImplementation("junit:junit:4.13.2") {
        exclude(group = "org.hamcrest")
    }
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("org.awaitility:awaitility:4.2.0")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.4")
}

// configuration for gradle-intellij-plugin plugin.
// read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    version.set(properties("platformVersion"))
    // https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-type
    // type.set("GO")
    downloadSources.set(properties("platformDownloadSources").toBoolean())

    // plugin dependencies: uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

// configure for detekt plugin.
// read more: https://detekt.github.io/detekt/kotlindsl.html
detekt {
    config.setFrom("$projectDir/.github/detekt/detekt-config.yml")
    baseline = file("$projectDir/.github/detekt/detekt-baseline.xml")
    buildUponDefaultConfig = true
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = jdk
        kotlinOptions.languageVersion = "1.9"
    }

    withType<JavaCompile> {
        targetCompatibility = jdk
        sourceCompatibility = jdk
    }

    withType<Detekt> {
        jvmTarget = jdk
        reports {
            sarif {
                required.set(true)
                outputLocation.set(file("$buildDir/detekt.sarif"))
            }
            html.required.set(false)
            xml.required.set(false)
            txt.required.set(false)
        }
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
        maxHeapSize = "2048m"
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    val createOpenApiSourceJar by registering(Jar::class) {
        // Java sources
        from(sourceSets.main.get().java) {
            include("**/*.java")
        }
        // Kotlin sources
        from(kotlin.sourceSets.main.get().kotlin) {
            include("**/*.kt")
        }
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        archiveClassifier.set("src")
    }

    buildPlugin {
        dependsOn(createOpenApiSourceJar)
        from(createOpenApiSourceJar) { into("lib/src") }
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
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

        changeNotes.set(provider { changelog.renderItem(changelog.getLatest(), Changelog.OutputType.HTML) })
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
    }
}
