package icons

import com.intellij.openapi.util.IconLoader.getIcon
import javax.swing.ImageIcon

object SnykIcons {
    private fun getIconFromResources(name: String): ImageIcon = ImageIcon(this::class.java.getResource(name))

    @JvmField
    val TOOL_WINDOW = getIcon("/icons/toolWindowSnyk.svg")

    val VULNERABILITY_16 = getIconFromResources("/icons/vulnerability_16.png")
    val VULNERABILITY_24 = getIconFromResources("/icons/vulnerability.png")

    val HIGH_SEVERITY = getIcon("/icons/severity_high.svg")
    val LOW_SEVERITY = getIcon("/icons/severity_low.svg")
    val MEDIUM_SEVERITY = getIcon("/icons/severity_medium.svg")

    val GRADLE = getIcon("/icons/gradle.svg")
    val MAVEN = getIcon("/icons/maven.svg")
    val NPM = getIcon("/icons/npm.svg")
    val PYTHON = getIcon("/icons/python.svg")
}
