package io.snyk.plugin.ui

import com.intellij.util.SVGLoader
import javax.swing.ImageIcon

class Icons {
    companion object {
        private fun getIconFromResources(name: String): ImageIcon = ImageIcon(this::class.java.getResource(name))
        private fun getSvgIconFromResources(name: String): ImageIcon =
            ImageIcon(SVGLoader.load(this::class.java.getResource(name), 1.0f))

        val VULNERABILITY_24 = getIconFromResources("/icons/vulnerability.png")
        val VULNERABILITY_16 = getIconFromResources("/icons/vulnerability_16.png")
        val HIGH_SEVERITY = getIconFromResources("/icons/high_severity_16.png")
        val LOW_SEVERITY = getIconFromResources("/icons/low_severity_16.png")
        val MEDIUM_SEVERITY = getIconFromResources("/icons/medium_severity_16.png")

        val GRADLE = getSvgIconFromResources("/icons/gradle.svg")
        val MAVEN = getSvgIconFromResources("/icons/maven.svg")
        val NPM = getSvgIconFromResources("/icons/npm.svg")
        val PYTHON = getSvgIconFromResources("/icons/python.svg")
    }
}
