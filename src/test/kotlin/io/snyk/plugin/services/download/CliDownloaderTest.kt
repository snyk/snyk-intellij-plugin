package io.snyk.plugin.services.download

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CliDownloaderTest {
    @Test
    fun `calculateSha256 should verify the SHA of a given file and return false if no match`() {
        val filePath = "src/test/resources/dummy-binary"
        val expectedSha = "2b418a5d0573164b4f93188fc94de0332fc0968e7a8439b01f530a4cdde1dcf2"
        val bytes = Files.readAllBytes(File(filePath).toPath())

        assertEquals(expectedSha, CliDownloader().calculateSha256(bytes))
    }

    @Test
    fun `should refer to snyk static website as base url`() {
        assertEquals("https://static.snyk.io", CliDownloader.BASE_URL)
    }

    @Test
    fun `should download version information from base url`() {
        assertEquals("${CliDownloader.BASE_URL}/cli/latest/version", CliDownloader.LATEST_RELEASES_URL)
    }

    @Test
    fun `should download cli information from base url`() {
        assertEquals("${CliDownloader.BASE_URL}/cli/latest/%s", CliDownloader.LATEST_RELEASE_DOWNLOAD_URL)
    }

    @Test
    fun `should download sha256 from base url`() {
        assertEquals("${CliDownloader.BASE_URL}/cli/latest/%s.sha256", CliDownloader.SHA256_DOWNLOAD_URL)
    }
}
