package io.snyk.plugin.cli

import java.io.IOException
import java.nio.file.Paths
import java.util.Properties

class Platform(val snykWrapperFileName: String) {
    companion object {
        val LINUX = Platform("snyk-linux")
        val LINUX_ALPINE = Platform("snyk-alpine")
        val MAC_OS = Platform("snyk-macos")
        val WINDOWS = Platform("snyk-win.exe")

        @Throws(PlatformDetectionException::class)
        fun current(): Platform = detect(System.getProperties())

        @Suppress("MoveVariableDeclarationIntoWhen")
        @Throws(PlatformDetectionException::class)
        fun detect(systemProperties: Properties): Platform {
            val architectureName = (systemProperties["os.name"] as String).lowercase()
            return when (architectureName) {
                "linux" -> if (Paths.get("/etc/alpine-release").toFile().exists()) LINUX_ALPINE else LINUX
                "mac os x", "darwin", "osx" -> MAC_OS
                else -> {
                    if (architectureName.contains("windows")) {
                        WINDOWS
                    } else {
                        throw PlatformDetectionException("$architectureName is not supported CPU type")
                    }
                }
            }
        }
    }
}

class PlatformDetectionException(exceptionMessage: String) : IOException(exceptionMessage)
