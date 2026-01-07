import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// reads properties from gradle.properties file
fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("org.jetbrains.changelog") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("io.gitlab.arturbosch.detekt") version ("1.23.6")
    id("pl.allegro.tech.build.axion-release") version "1.21.1"
}

version = scmVersion.version

group = properties("pluginGroup")
description = properties("pluginName")

val jdk = "21"

repositories {
    mavenCentral()
    mavenLocal()
    intellijPlatform {
        defaultRepositories()
    }
    maven {
        url = uri("https://www.jetbrains.com/intellij-repository/releases")
    }
    maven {
        url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(properties("platformVersion"))
        bundledPlugin("com.intellij.java")

        plugins(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
        testFramework(TestFrameworkType.Platform)
    }

    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation(platform("com.squareup.retrofit2:retrofit-bom:2.11.0"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.23.1")

    implementation("org.commonmark:commonmark:0.21.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.sentry:sentry:6.27.0")
    implementation("javax.xml.bind:jaxb-api:2.3.1") // necessary because since JDK 9 not included
    implementation("com.squareup.retrofit2:retrofit")
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation("org.json:json:20231013")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.16")

    testImplementation("com.google.jimfs:jimfs:1.3.0")
    testImplementation("com.squareup.okhttp3:mockwebserver")

    testImplementation("junit:junit:4.13.2") {
        exclude(group = "org.hamcrest")
    }
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("io.mockk:mockk:1.14.7")
    testImplementation("org.awaitility:awaitility:4.2.0")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
}

// Configure the IntelliJ Platform Gradle Plugin
intellijPlatform {
    // Required configuration for IntelliJ Platform dependencies
    pluginConfiguration {
        id.set(properties("pluginId"))
        name.set(properties("pluginName"))
        version.set(project.version.toString())
        description.set("Snyk helps you find, fix and monitor for known vulnerabilities in your dependencies")

        ideaVersion {
            sinceBuild.set(properties("pluginSinceBuild"))
            untilBuild.set(properties("pluginUntilBuild"))
        }
    }

    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
        }
        freeArgs.set(listOf("-mute","TemplateWordInPluginId"))
        failureLevel.set(
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
                VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            )
        )
    }
}

// Configure for detekt plugin
detekt {
    config.setFrom("$projectDir/.github/detekt/detekt-config.yml")
    baseline = file("$projectDir/.github/detekt/detekt-baseline.xml")
    buildUponDefaultConfig = true
}

// Configure gradle-changelog-plugin
changelog {
    version.set(project.version.toString())
    groups.set(emptyList())
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = jdk
        targetCompatibility = jdk
    }

    withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jdk))
        compilerOptions.languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }

    withType<Detekt> {
        jvmTarget = jdk
        reports {
            sarif {
                required.set(true)
                // this deprecation is needed to make this work
                outputLocation.set(file("$buildDir/detekt.sarif"))
            }
            html.required.set(false)
            xml.required.set(false)
            txt.required.set(false)
        }
    }

    withType<Test> {
        maxHeapSize = "4096m"
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    // Configure the PatchPluginXml task
    patchPluginXml {
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        val content = File("$projectDir/README.md").readText()
        val startIndex = content.indexOf("# JetBrains plugin")
        val descriptionFromReadme =
            content.substring(startIndex).lines().joinToString("\n").run { markdownToHTML(this) }
        pluginDescription.set(descriptionFromReadme)

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider { changelog.renderItem(changelog.getLatest(), Changelog.OutputType.HTML) })
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

    verifyPlugin {
        mustRunAfter(patchPluginXml)
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }
}
