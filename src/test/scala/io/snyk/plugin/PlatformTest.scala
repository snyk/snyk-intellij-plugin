package io.snyk.plugin

import java.util

import io.snyk.plugin.client.{Platform, PlatformDetectionException}
import org.junit.Test
import org.junit.Assert.assertTrue

class PlatformTest {

  @Test
  def testDetectPlatform(): Unit = {
    val properties: util.Map[AnyRef, AnyRef] = new util.HashMap[AnyRef, AnyRef]()

    properties.put("os.name", "linux")

    val linuxPlatform = Platform.detect(properties)
    assertTrue(Platform.Linux == linuxPlatform || Platform.LinuxAlpine == linuxPlatform)

    properties.put("os.name", "mac os x")
    assertTrue(Platform.MacOS == Platform.detect(properties))

    properties.put("os.name", "darwin")
    assertTrue(Platform.MacOS == Platform.detect(properties))

    properties.put("os.name", "osx")
    assertTrue(Platform.MacOS == Platform.detect(properties))

    properties.put("os.name", "windows")
    assertTrue(Platform.Windows == Platform.detect(properties))
  }

  @Test(expected = classOf[PlatformDetectionException])
  def testDetectPlatformException(): Unit = {
    val properties: util.Map[AnyRef, AnyRef] = new util.HashMap[AnyRef, AnyRef]()
    properties.put("os.name", "Not supported CPU type")

    Platform.detect(properties)
  }
}
