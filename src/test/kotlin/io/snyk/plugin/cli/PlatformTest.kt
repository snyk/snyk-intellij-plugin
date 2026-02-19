package io.snyk.plugin.cli

import java.util.Properties
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTest {

  @Test
  fun testDetectPlatform() {
    val properties = Properties()
    properties["os.arch"] = "something"

    properties["os.name"] = "linux"

    val linuxPlatform = Platform.detect(properties)
    assertTrue(Platform.LINUX == linuxPlatform || Platform.LINUX_ALPINE == linuxPlatform)

    properties["os.name"] = "linux"
    properties["os.arch"] = "aarch64"
    val arm64Platform = Platform.detect(properties)
    assertTrue(
      Platform.LINUX_ARM64 == arm64Platform || Platform.LINUX_ALPINE_ARM64 == arm64Platform
    )

    properties["os.name"] = "linux"
    properties["os.arch"] = "arm64"
    val arm64Platform2 = Platform.detect(properties)
    assertTrue(
      Platform.LINUX_ARM64 == arm64Platform2 || Platform.LINUX_ALPINE_ARM64 == arm64Platform2
    )

    properties["os.name"] = "mac os x"
    properties["os.arch"] = "something"
    assertTrue(Platform.MAC_OS == Platform.detect(properties))

    properties["os.name"] = "osx"
    assertTrue(Platform.MAC_OS == Platform.detect(properties))

    properties["os.name"] = "darwin"
    assertTrue(Platform.MAC_OS == Platform.detect(properties))

    properties["os.name"] = "darwin"
    properties["os.arch"] = "aarch64"
    assertTrue(Platform.MAC_OS_ARM64 == Platform.detect(properties))

    properties["os.name"] = "windows"
    assertTrue(Platform.WINDOWS == Platform.detect(properties))
  }

  @Test(expected = PlatformDetectionException::class)
  fun testDetectPlatformException() {
    val properties = Properties()
    properties["os.name"] = "Not supported CPU type"
    properties["os.arch"] = "dont care"

    Platform.detect(properties)
  }
}
