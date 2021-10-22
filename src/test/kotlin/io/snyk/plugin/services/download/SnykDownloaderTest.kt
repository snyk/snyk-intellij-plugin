package io.snyk.plugin.services.download

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SnykDownloaderTest {
    @Test
    fun `calculateSha256 should verify the SHA of a given file and return false if no match`() {
        val filePath = "src/test/resources/npm-test-vulnerability.json"
        val expectedSha = "dd307dd1effb43473562d02ccef9ea1f925b6f8364e85e85077b72335019a4d3"
        val bytes = Files.readAllBytes(File(filePath).toPath())

        assertEquals(expectedSha, SnykDownloader().calculateSha256(bytes))
    }

    @Test
    fun `should refer to snyk static website as base url`() {
        assertEquals("https://static.snyk.io", SnykDownloader.BASE_URL)
    }

    @Test
    fun `should download version information from base url`() {
        assertEquals("${SnykDownloader.BASE_URL}/cli/latest/version", SnykDownloader.LATEST_RELEASES_URL)
    }

    @Test
    fun `should download cli information from base url`() {
        assertEquals("${SnykDownloader.BASE_URL}/cli/latest/%s", SnykDownloader.LATEST_RELEASE_DOWNLOAD_URL)
    }

    @Test
    fun `should download sha256 from base url`() {
        assertEquals("${SnykDownloader.BASE_URL}/cli/latest/%s.sha256", SnykDownloader.SHA256_DOWNLOAD_URL)
    }
}
