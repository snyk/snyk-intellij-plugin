package io.snyk.plugin.services.download

import com.intellij.openapi.progress.ProgressIndicator
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import java.io.File
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    fun downloadFile(cliFile: File, expectedSha: String, indicator: ProgressIndicator): File {
        indicator.checkCanceled()
        val downloadFile = File.createTempFile(cliFile.name, ".download", cliFile.parentFile)
        try {
            downloadFile.deleteOnExit()

            val url = URL(String.format(LATEST_RELEASE_DOWNLOAD_URL, Platform.current().snykWrapperFileName)).toString()

            indicator.checkCanceled()
            createRequest(url).saveToFile(downloadFile, indicator)

            indicator.checkCanceled()
            verifyChecksum(expectedSha, downloadFile.readBytes())

            indicator.checkCanceled()
            if (cliFile.exists()) {
                cliFile.delete()
            }
            try {
                Files.move(
                    downloadFile.toPath(),
                    cliFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: AtomicMoveNotSupportedException) {
                // fallback to renameTo because of e
                downloadFile.renameTo(cliFile)
            }
            cliFile.setExecutable(true)
            return cliFile
        } finally {
            if (downloadFile.exists()) {
                downloadFile.delete()
            }
        }
    }

    fun expectedSha(): String {
        val url = String.format(SHA256_DOWNLOAD_URL, Platform.current().snykWrapperFileName)
        val request = createRequest(url)
        return request.readString().split(" ")[0]
    }
}
