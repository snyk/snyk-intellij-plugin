package snyk.oss.annotator

object AnnotatorHelper {

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
