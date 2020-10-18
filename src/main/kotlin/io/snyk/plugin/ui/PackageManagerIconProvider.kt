package io.snyk.plugin.ui

import com.intellij.icons.AllIcons
import icons.SnykIcons
import javax.swing.Icon

class PackageManagerIconProvider {

    companion object {
        fun getIcon(packageManagerKey: String): Icon = when (packageManagerKey) {
            "rubygems" -> SnykIcons.RUBY_GEMS
            "npm" -> SnykIcons.NPM
            "yarn", "yarn-workspace" -> SnykIcons.YARN
            "maven" -> SnykIcons.MAVEN
            "pip" -> SnykIcons.PYTHON
            "sbt" -> SnykIcons.SBT
            "gradle" -> SnykIcons.GRADLE
            "golangdep" -> SnykIcons.GOlANG_DEP
            "govendor" -> SnykIcons.GO_VENDOR
            "golang", "gomodules" -> SnykIcons.GOLANG
            "nuget" -> SnykIcons.NUGET
            "paket" -> SnykIcons.PAKET
            "composer" -> SnykIcons.COMPOSER
            "linux" -> SnykIcons.LINUX
            "deb" -> SnykIcons.DEB
            "apk" -> SnykIcons.APK
            "cocoapods" -> SnykIcons.COCOAPODS
            "rpm" -> SnykIcons.RPM
            "dockerfile" -> SnykIcons.DOCKER
            else -> AllIcons.FileTypes.Text
        }
    }
}
