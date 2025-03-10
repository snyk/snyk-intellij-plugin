package icons

import com.intellij.openapi.util.IconLoader.getIcon
import io.snyk.plugin.Severity
import javax.swing.Icon
import javax.swing.ImageIcon

object SnykIcons {
    private fun getIconFromResources(name: String): ImageIcon = ImageIcon(this::class.java.getResource(name))

    @JvmField
    val TOOL_WINDOW = getIcon("/icons/snyk-dog.svg", SnykIcons::class.java)

    val LOGO = getIcon("/icons/logo_snyk.png", SnykIcons::class.java)

    private val VULNERABILITY_16 = getIconFromResources("/icons/vulnerability_16.png")
    private val VULNERABILITY_24 = getIconFromResources("/icons/vulnerability.png")

    val OPEN_SOURCE_SECURITY = getIcon("/icons/oss.svg", SnykIcons::class.java)
    val OPEN_SOURCE_SECURITY_DISABLED = getIcon("/icons/oss_disabled.svg", SnykIcons::class.java)
    val SNYK_CODE = getIcon("/icons/code.svg", SnykIcons::class.java)
    val SNYK_CODE_DISABLED = getIcon("/icons/code_disabled.svg", SnykIcons::class.java)
    val IAC = getIcon("/icons/iac.svg", SnykIcons::class.java)
    val IAC_DISABLED = getIcon("/icons/iac_disabled.svg", SnykIcons::class.java)
    val CONTAINER = getIcon("/icons/container.svg", SnykIcons::class.java)
    val CONTAINER_DISABLED = getIcon("/icons/container_disabled.svg", SnykIcons::class.java)

    val CONTAINER_IMAGE = getIcon("/icons/container_image.svg", SnykIcons::class.java)
    val CONTAINER_IMAGE_24 = getIcon("/icons/container_image_24.svg", SnykIcons::class.java)

    val GRADLE = getIcon("/icons/gradle.svg", SnykIcons::class.java)
    val MAVEN = getIcon("/icons/maven.svg", SnykIcons::class.java)
    val NPM = getIcon("/icons/npm.svg", SnykIcons::class.java)
    val PYTHON = getIcon("/icons/python.svg", SnykIcons::class.java)
    val RUBY_GEMS = getIcon("/icons/rubygems.svg", SnykIcons::class.java)
    val YARN = getIcon("/icons/yarn.svg", SnykIcons::class.java)
    val SBT = getIcon("/icons/sbt.svg", SnykIcons::class.java)
    val GOlANG_DEP = getIcon("/icons/golangdep.svg", SnykIcons::class.java)
    val GO_VENDOR = getIcon("/icons/govendor.svg", SnykIcons::class.java)
    val GOLANG = getIcon("/icons/golang.svg", SnykIcons::class.java)
    val NUGET = getIcon("/icons/nuget.svg", SnykIcons::class.java)
    val PAKET = getIcon("/icons/paket.svg", SnykIcons::class.java)
    val COMPOSER = getIcon("/icons/composer.svg", SnykIcons::class.java)
    val LINUX = getIcon("/icons/linux.svg", SnykIcons::class.java)
    val DEB = getIcon("/icons/deb.svg", SnykIcons::class.java)
    val APK = getIcon("/icons/apk.svg", SnykIcons::class.java)
    val COCOAPODS = getIcon("/icons/cocoapods.svg", SnykIcons::class.java)
    val RPM = getIcon("/icons/rpm.svg", SnykIcons::class.java)
    val DOCKER = getIcon("/icons/docker.svg", SnykIcons::class.java)

    // copy of FeaturesTrainerIcons.Img.GreenCheckmark from https://jetbrains.github.io/ui/resources/icons_list/
    val CHECKMARK_GREEN = getIcon("/icons/greenCheckmark.svg", SnykIcons::class.java)

    private val CRITICAL_SEVERITY_16 = getIcon("/icons/dark-critical-severity.svg", SnykIcons::class.java)
    private val CRITICAL_SEVERITY_32 = getIcon("/icons/severity_critical_32.svg", SnykIcons::class.java)
    private val HIGH_SEVERITY_16 = getIcon("/icons/dark-high-severity.svg", SnykIcons::class.java)
    private val HIGH_SEVERITY_32 = getIcon("/icons/severity_high_32.svg", SnykIcons::class.java)
    private val LOW_SEVERITY_16 = getIcon("/icons/dark-low-severity.svg", SnykIcons::class.java)
    private val LOW_SEVERITY_32 = getIcon("/icons/severity_low_32.svg", SnykIcons::class.java)
    private val MEDIUM_SEVERITY_16 = getIcon("/icons/dark-medium-severity.svg", SnykIcons::class.java)
    private val MEDIUM_SEVERITY_32 = getIcon("/icons/severity_medium_32.svg", SnykIcons::class.java)

    fun getSeverityIcon(severity: Severity, iconSize: IconSize = IconSize.SIZE16): Icon {
        return when (severity) {
            Severity.CRITICAL -> when (iconSize) {
                IconSize.SIZE16 -> CRITICAL_SEVERITY_16
                IconSize.SIZE32 -> CRITICAL_SEVERITY_32
            }

            Severity.HIGH -> when (iconSize) {
                IconSize.SIZE16 -> HIGH_SEVERITY_16
                IconSize.SIZE32 -> HIGH_SEVERITY_32
            }
            Severity.MEDIUM -> when (iconSize) {
                IconSize.SIZE16 -> MEDIUM_SEVERITY_16
                IconSize.SIZE32 -> MEDIUM_SEVERITY_32
            }
            Severity.LOW -> when (iconSize) {
                IconSize.SIZE16 -> LOW_SEVERITY_16
                IconSize.SIZE32 -> LOW_SEVERITY_32
            }
            else -> when (iconSize) {
                IconSize.SIZE16 -> VULNERABILITY_16
                IconSize.SIZE32 -> VULNERABILITY_24
            }
        }
    }

    enum class IconSize {
        SIZE16, SIZE32
    }
}
