package snyk.oss.annotator

import com.intellij.lang.annotation.HighlightSeverity
import snyk.common.SeverityConstants
import snyk.oss.Vulnerability

object AnnotatorHelper {
    fun severity(vulnerability: Vulnerability): HighlightSeverity {
        return when (vulnerability.severity) {
            SeverityConstants.SEVERITY_CRITICAL -> HighlightSeverity.ERROR
            SeverityConstants.SEVERITY_HIGH -> HighlightSeverity.WARNING
            SeverityConstants.SEVERITY_MEDIUM -> HighlightSeverity.WEAK_WARNING
            SeverityConstants.SEVERITY_LOW -> HighlightSeverity.INFORMATION
            else -> HighlightSeverity.INFORMATION
        }
    }

    fun hasDedicatedAnnotator(filePath: String) =
        filePath.endsWith("pom.xml") ||
            filePath.endsWith("go.mod") ||
            filePath.endsWith("build.gradle") ||
            filePath.endsWith("build.gradle.kts")

    fun isFileSupported(filePath: String): Boolean =
        listOf(
            "arn.lock",
            "package-lock.json",
            "package.json",
            "Gemfile",
            "Gemfile.lock",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "build.sbt",
            "Pipfile",
            "requirements.txt",
            "Gopkg.lock",
            "go.mod",
            "vendor/vendor.json",
            "obj/project.assets.json",
            "project.assets.json",
            "packages.config",
            "paket.dependencies",
            "composer.lock",
            "Podfile",
            "Podfile.lock",
            "poetry.lock",
            "mix.exs",
            "mix.lock"
        ).any { filePath.toLowerCase().endsWith(it) }
}
