package icons

import com.intellij.openapi.util.IconLoader.getIcon
import io.snyk.plugin.Severity
import javax.swing.Icon
import javax.swing.ImageIcon

object SnykIcons {
    private fun getIconFromResources(name: String): ImageIcon = ImageIcon(this::class.java.getResource(name))

    @JvmField
    val TOOL_WINDOW = getIcon("/icons/snyk-dog.svg", SnykIcons.javaClass)

    val LOGO = getIcon("/icons/logo_snyk.png", SnykIcons.javaClass)

    val VULNERABILITY_16 = getIconFromResources("/icons/vulnerability_16.png")
    val VULNERABILITY_24 = getIconFromResources("/icons/vulnerability.png")

    val OPEN_SOURCE_SECURITY = getIcon("/icons/oss.svg", SnykIcons.javaClass)
    val OPEN_SOURCE_SECURITY_DISABLED = getIcon("/icons/oss_disabled.svg", SnykIcons.javaClass)
    val SNYK_CODE = getIcon("/icons/code.svg", SnykIcons.javaClass)
    val SNYK_CODE_DISABLED = getIcon("/icons/code_disabled.svg", SnykIcons.javaClass)
    val IAC = getIcon("/icons/iac.svg", SnykIcons.javaClass)
    val IAC_DISABLED = getIcon("/icons/iac_disabled.svg", SnykIcons.javaClass)
    val CONTAINER = getIcon("/icons/container.svg", SnykIcons.javaClass)
    val CONTAINER_DISABLED = getIcon("/icons/container_disabled.svg", SnykIcons.javaClass)
    val CONTAINER_IMAGE = getIcon("/icons/container_image.svg", SnykIcons.javaClass)
    val CONTAINER_IMAGE_24 = getIcon("/icons/container_image_24.svg", SnykIcons.javaClass)

    val GRADLE = getIcon("/icons/gradle.svg", SnykIcons.javaClass)
    val MAVEN = getIcon("/icons/maven.svg", SnykIcons.javaClass)
    val NPM = getIcon("/icons/npm.svg", SnykIcons.javaClass)
    val PYTHON = getIcon("/icons/python.svg", SnykIcons.javaClass)
    val RUBY_GEMS = getIcon("/icons/rubygems.svg", SnykIcons.javaClass)
    val YARN = getIcon("/icons/yarn.svg", SnykIcons.javaClass)
    val SBT = getIcon("/icons/sbt.svg", SnykIcons.javaClass)
    val GOlANG_DEP = getIcon("/icons/golangdep.svg", SnykIcons.javaClass)
    val GO_VENDOR = getIcon("/icons/govendor.svg", SnykIcons.javaClass)
    val GOLANG = getIcon("/icons/golang.svg", SnykIcons.javaClass)
    val NUGET = getIcon("/icons/nuget.svg", SnykIcons.javaClass)
    val PAKET = getIcon("/icons/paket.svg", SnykIcons.javaClass)
    val COMPOSER = getIcon("/icons/composer.svg", SnykIcons.javaClass)
    val LINUX = getIcon("/icons/linux.svg", SnykIcons.javaClass)
    val DEB = getIcon("/icons/deb.svg", SnykIcons.javaClass)
    val APK = getIcon("/icons/apk.svg", SnykIcons.javaClass)
    val COCOAPODS = getIcon("/icons/cocoapods.svg", SnykIcons.javaClass)
    val RPM = getIcon("/icons/rpm.svg", SnykIcons.javaClass)
    val DOCKER = getIcon("/icons/docker.svg", SnykIcons.javaClass)

    // copy of FeaturesTrainerIcons.Img.GreenCheckmark from https://jetbrains.github.io/ui/resources/icons_list/
    val CHECKMARK_GREEN = getIcon("/icons/greenCheckmark.svg", SnykIcons.javaClass)

    private val CRITICAL_SEVERITY_16 = getIcon("/icons/severity_critical_16.svg", SnykIcons.javaClass)
    private val CRITICAL_SEVERITY_32 = getIcon("/icons/severity_critical_32.svg", SnykIcons.javaClass)
    private val HIGH_SEVERITY_16 = getIcon("/icons/severity_high_16.svg", SnykIcons.javaClass)
    private val HIGH_SEVERITY_32 = getIcon("/icons/severity_high_32.svg", SnykIcons.javaClass)
    private val LOW_SEVERITY_16 = getIcon("/icons/severity_low_16.svg", SnykIcons.javaClass)
    private val LOW_SEVERITY_32 = getIcon("/icons/severity_low_32.svg", SnykIcons.javaClass)
    private val MEDIUM_SEVERITY_16 = getIcon("/icons/severity_medium_16.svg", SnykIcons.javaClass)
    private val MEDIUM_SEVERITY_32 = getIcon("/icons/severity_medium_32.svg", SnykIcons.javaClass)

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
