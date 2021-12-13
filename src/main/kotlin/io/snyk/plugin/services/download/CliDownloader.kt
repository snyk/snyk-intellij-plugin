package io.snyk.plugin.services.download

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import io.snyk.plugin.cli.Platform
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

class CliDownloader {
    companion object {
        const val BASE_URL = "https://static.snyk.io"
        const val LATEST_RELEASES_URL = "$BASE_URL/cli/latest/version"
        const val LATEST_RELEASE_DOWNLOAD_URL = "$BASE_URL/cli/latest/%s"
        const val SHA256_DOWNLOAD_URL = "$BASE_URL/cli/latest/%s.sha256"
    }

    fun calculateSha256(bytes: ByteArray): String {
        return DatatypeConverter.printHexBinary(
            MessageDigest.getInstance("SHA-256").digest(bytes)
        ).toLowerCase()
    }

    @Throws(ChecksumVerificationException::class)
    fun verifyChecksum(expectedSha: String, bytes: ByteArray) {
        val sha256 = calculateSha256(bytes)
        if (sha256.toLowerCase() != expectedSha.toLowerCase()) {
            throw ChecksumVerificationException("Expected $expectedSha, but downloaded file has $sha256")
        }
    }

    fun downloadFile(cliFile: File, indicator: ProgressIndicator): File {
        val snykWrapperFileName = Platform.current().snykWrapperFileName
        val url = URL(java.lang.String.format(LATEST_RELEASE_DOWNLOAD_URL, snykWrapperFileName)).toString()

        if (cliFile.exists()) {
            cliFile.delete()
        }

        val shaUrl = URL(String.format(SHA256_DOWNLOAD_URL, Platform.current().snykWrapperFileName))
        val expectedSha = shaUrl.readText(Charsets.UTF_8).split(" ")[0]

        HttpRequests
            .request(url)
            .productNameAsUserAgent()
            .saveToFile(cliFile, indicator)

        verifyChecksum(expectedSha, cliFile.readBytes())
        cliFile.setExecutable(true)
        return cliFile
    }
}
