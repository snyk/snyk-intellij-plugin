package io.snyk.plugin.services.download

import com.intellij.openapi.progress.ProgressIndicator
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import javax.xml.bind.DatatypeConverter

class CliDownloader {
    companion object {
        val BASE_URL: String
            get() = pluginSettings().cliBaseDownloadURL
        val LATEST_RELEASES_URL: String
            get() = "$BASE_URL/cli/latest/version"
        val LATEST_RELEASE_DOWNLOAD_URL: String
            get() = "$BASE_URL/cli/latest/${Platform.current().snykWrapperFileName}"
        val SHA256_DOWNLOAD_URL: String
            get() = "$BASE_URL/cli/latest/${Platform.current().snykWrapperFileName}.sha256"
    }

    fun calculateSha256(bytes: ByteArray): String {
        return DatatypeConverter.printHexBinary(
            MessageDigest.getInstance("SHA-256").digest(bytes)
        ).lowercase(Locale.getDefault())
    }

    @Throws(ChecksumVerificationException::class)
    fun verifyChecksum(expectedSha: String, bytes: ByteArray) {
        val sha256 = calculateSha256(bytes)
        if (sha256.lowercase(Locale.getDefault()) != expectedSha.lowercase(Locale.getDefault())) {
            throw ChecksumVerificationException("Expected $expectedSha, but downloaded file has $sha256")
        }
    }

    fun downloadFile(cliFile: File, expectedSha: String, indicator: ProgressIndicator): File {
        indicator.checkCanceled()
        val downloadFile = try {
            File.createTempFile(cliFile.name, ".download", cliFile.parentFile)
        } catch (e: Exception) {
            val message = "Cannot create file in the configured CLI path directory ${cliFile.parent}. " +
                "Please either change the CLI path to a writeable directory or give the " +
                "current directory write permissions."
            throw IOException(message, e)
        }
        try {
            downloadFile.deleteOnExit()

            indicator.checkCanceled()
            createRequest(LATEST_RELEASE_DOWNLOAD_URL).saveToFile(downloadFile, indicator)

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
            } catch (ignored: AtomicMoveNotSupportedException) {
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
        val request = createRequest(SHA256_DOWNLOAD_URL)
        return request.readString().split(" ")[0]
    }
}
