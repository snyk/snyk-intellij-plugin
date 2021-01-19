package icons

import com.intellij.openapi.util.IconLoader.getIcon
import javax.swing.ImageIcon

object SnykIcons {
    private fun getIconFromResources(name: String): ImageIcon = ImageIcon(this::class.java.getResource(name))

    @JvmField
    val TOOL_WINDOW = getIcon("/icons/toolWindowSnyk.svg")

    val LOGO = getIcon("/icons/logo_snyk.png")

    val VULNERABILITY_16 = getIconFromResources("/icons/vulnerability_16.png")
    val VULNERABILITY_24 = getIconFromResources("/icons/vulnerability.png")

    val OPEN_SOURCE_SECURITY =  getIcon("/icons/oss.svg")
    val SNYK_CODE =  getIcon("/icons/code.svg")

    val HIGH_SEVERITY = getIcon("/icons/severity_high.svg")
    val LOW_SEVERITY = getIcon("/icons/severity_low.svg")
    val MEDIUM_SEVERITY = getIcon("/icons/severity_medium.svg")

    val GRADLE = getIcon("/icons/gradle.svg")
    val MAVEN = getIcon("/icons/maven.svg")
    val NPM = getIcon("/icons/npm.svg")
    val PYTHON = getIcon("/icons/python.svg")
    val RUBY_GEMS = getIcon("/icons/rubygems.svg")
    val YARN = getIcon("/icons/yarn.svg")
    val SBT = getIcon("/icons/sbt.svg")
    val GOlANG_DEP = getIcon("/icons/golangdep.svg")
    val GO_VENDOR = getIcon("/icons/govendor.svg")
    val GOLANG = getIcon("/icons/golang.svg")
    val NUGET = getIcon("/icons/nuget.svg")
    val PAKET = getIcon("/icons/paket.svg")
    val COMPOSER = getIcon("/icons/composer.svg")
    val LINUX = getIcon("/icons/linux.svg")
    val DEB = getIcon("/icons/deb.svg")
    val APK = getIcon("/icons/apk.svg")
    val COCOAPODS = getIcon("/icons/cocoapods.svg")
    val RPM = getIcon("/icons/rpm.svg")
    val DOCKER = getIcon("/icons/docker.svg")
}
