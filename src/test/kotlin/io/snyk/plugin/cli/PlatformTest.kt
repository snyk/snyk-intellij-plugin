package io.snyk.plugin.cli

import org.junit.Test
import tlschannel.util.Util.assertTrue
import java.util.*

class PlatformTest {

    @Test
    fun testDetectPlatform() {
        val properties = Properties()

        properties["os.name"] = "linux"

        val linuxPlatform = Platform.detect(properties)
        assertTrue(Platform.LINUX == linuxPlatform || Platform.LINUX_ALPINE == linuxPlatform)

        properties["os.name"] = "mac os x"
        assertTrue(Platform.MAC_OS == Platform.detect(properties))

        properties["os.name"] = "darwin"
        assertTrue(Platform.MAC_OS == Platform.detect(properties))

        properties["os.name"] = "osx"
        assertTrue(Platform.MAC_OS == Platform.detect(properties))

        properties["os.name"] = "windows"
        assertTrue(Platform.WINDOWS == Platform.detect(properties))
    }

    @Test(expected = PlatformDetectionException::class)
    fun testDetectPlatformException() {
        val properties = Properties()
        properties["os.name"] = "Not supported CPU type"

        Platform.detect(properties)
    }
}
