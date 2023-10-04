package io.snyk.plugin.cli

import java.io.IOException
import java.nio.file.Paths
import java.util.Properties

class Platform(val snykWrapperFileName: String) {
    companion object {
        val LINUX = Platform("snyk-linux")
        val LINUX_ALPINE = Platform("snyk-alpine")
        val MAC_OS = Platform("snyk-macos")
        val MAC_OS_ARM64 = Platform("snyk-macos-arm64")
        val WINDOWS = Platform("snyk-win.exe")

        @Throws(PlatformDetectionException::class)
        fun current(): Platform = detect(System.getProperties())

        @Throws(PlatformDetectionException::class)
        fun detect(systemProperties: Properties): Platform {
            val osName = (systemProperties["os.name"] as String).lowercase()
            val archName = (systemProperties["os.arch"] as String).lowercase()
            return when (osName) {
                "linux" -> if (Paths.get("/etc/alpine-release").toFile().exists()) LINUX_ALPINE else LINUX
                "mac os x", "darwin", "osx" -> if (archName != "aarch64") MAC_OS else MAC_OS_ARM64
                else -> {
                    if (osName.contains("windows")) {
                        WINDOWS
                    } else {
                        throw PlatformDetectionException("$osName is not supported CPU type")
                    }
                }
            }
        }
    }
}

class PlatformDetectionException(exceptionMessage: String) : IOException(exceptionMessage)
