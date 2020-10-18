package io.snyk.plugin.ui

import icons.SnykIcons
import org.junit.Test
import kotlin.test.assertEquals

class PackageManagerIconProviderTest {

    @Test
    fun testGetIcon() {
        assertEquals(SnykIcons.RUBY_GEMS, PackageManagerIconProvider.getIcon("rubygems"))
        assertEquals(SnykIcons.NPM, PackageManagerIconProvider.getIcon("npm"))
        assertEquals(SnykIcons.YARN, PackageManagerIconProvider.getIcon("yarn"))
        assertEquals(SnykIcons.YARN, PackageManagerIconProvider.getIcon("yarn-workspace"))
        assertEquals(SnykIcons.MAVEN, PackageManagerIconProvider.getIcon("maven"))
        assertEquals(SnykIcons.PYTHON, PackageManagerIconProvider.getIcon("pip"))
        assertEquals(SnykIcons.SBT, PackageManagerIconProvider.getIcon("sbt"))
        assertEquals(SnykIcons.GRADLE, PackageManagerIconProvider.getIcon("gradle"))
        assertEquals(SnykIcons.GOlANG_DEP, PackageManagerIconProvider.getIcon("golangdep"))
        assertEquals(SnykIcons.GO_VENDOR, PackageManagerIconProvider.getIcon("govendor"))
        assertEquals(SnykIcons.GOLANG, PackageManagerIconProvider.getIcon("gomodules"))
        assertEquals(SnykIcons.PAKET, PackageManagerIconProvider.getIcon("paket"))
        assertEquals(SnykIcons.COMPOSER, PackageManagerIconProvider.getIcon("composer"))
        assertEquals(SnykIcons.GOLANG, PackageManagerIconProvider.getIcon("golang"))
        assertEquals(SnykIcons.LINUX, PackageManagerIconProvider.getIcon("linux"))
        assertEquals(SnykIcons.DEB, PackageManagerIconProvider.getIcon("deb"))
        assertEquals(SnykIcons.APK, PackageManagerIconProvider.getIcon("apk"))
        assertEquals(SnykIcons.COCOAPODS, PackageManagerIconProvider.getIcon("cocoapods"))
        assertEquals(SnykIcons.RPM, PackageManagerIconProvider.getIcon("rpm"))
        assertEquals(SnykIcons.DOCKER, PackageManagerIconProvider.getIcon("dockerfile"))
    }
}
