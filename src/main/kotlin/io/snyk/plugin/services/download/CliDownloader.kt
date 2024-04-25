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
            get() = "$BASE_URL/cli/${pluginSettings().cliReleaseChannel}/ls-protocol-version-"+ pluginSettings().requiredLsProtocolVersion
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

    fun downloadFile(cliFile: File, cliVersion: String, indicator: ProgressIndicator): File {
        indicator.checkCanceled()
        val downloadFile = try {
            File.createTempFile(cliFile.name, ".download", cliFile.parentFile)
        } catch (e: Exception) {
            val message = "Cannot create file in the configured CLI path directory ${cliFile.parent}. " +
                "Please either change the CLI path to a writeable directory or give the " +
                "current directory write permissions."
            indicator.cancel()
            throw IOException(message, e)
        }
        try {
            downloadFile.deleteOnExit()

            indicator.checkCanceled()
            val downloadURL = getDownloadURL(cliVersion)
            createRequest(downloadURL).saveToFile(downloadFile, indicator)

            val expectedSha = expectedSha(cliVersion)

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

    fun expectedSha(cliVersion: String): String {
        val downloadURL = "${getDownloadURL(cliVersion)}.sha256"
        val request = createRequest(downloadURL)
        return request.readString().split(" ")[0]
    }

    private fun getDownloadURL(cliVersion: String) = "$BASE_URL/cli/v$cliVersion/${Platform.current().snykWrapperFileName}"
}
