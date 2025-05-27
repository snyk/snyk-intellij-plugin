package io.snyk.plugin.services.download

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.progress.ProgressIndicator
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.common.lsp.LanguageServerWrapper
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
            get() = "$BASE_URL/cli/${pluginSettings().cliReleaseChannel}/ls-protocol-version-" + pluginSettings().requiredLsProtocolVersion
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
        if (!cliFile.parentFile.exists()) {
            Files.createDirectories(cliFile.parentFile.toPath())
        }
        val downloadFile = try {
            File.createTempFile(cliFile.name, ".download", cliFile.parentFile)
        } catch (e: Exception) {
            val message = "Cannot create file in the configured CLI path directory ${cliFile.parent}. " +
                "Please either change the CLI path to a writeable directory or give the " +
                "current directory write permissions."
            indicator.cancel()
            throw IOException(message, e)
        }

        val lockedLS = mutableSetOf<LanguageServerWrapper>()
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

            ProjectUtil.getOpenProjects().forEach { project ->
                val languageServerWrapper = LanguageServerWrapper.getInstance(project)
                // prevent spawning of language server until files are moved
                languageServerWrapper.isInitializing.lock()
                try {
                    // shutdown, so the binary can be updated
                    languageServerWrapper.shutdown()
                } catch (_: Exception) {
                    // do nothing
                }
                lockedLS.add(languageServerWrapper)
            }
            try {
                Files.move(
                    downloadFile.toPath(),
                    cliFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // fallback to renameTo because of e
                val success = downloadFile.renameTo(cliFile)
                if (!success) {
                    val message =
                        "CLI could not be updated. Please check if another process is using the CLI binary at ${pluginSettings().cliPath}"
                    SnykBalloonNotificationHelper.showWarn(message, ProjectUtil.getActiveProject())
                }

            }
            cliFile.setExecutable(true)
            return cliFile
        } finally {
            if (downloadFile.exists()) {
                downloadFile.delete()
            }
            lockedLS.forEach {
                if (it.isInitializing.holdCount > 0) {
                    it.isInitializing.unlock()
                }
            }
        }
    }

    fun expectedSha(cliVersion: String): String {
        val downloadURL = "${getDownloadURL(cliVersion)}.sha256"
        val request = createRequest(downloadURL)
        return request.readString().split(" ")[0]
    }

    private fun getDownloadURL(cliVersion: String) =
        "$BASE_URL/cli/v$cliVersion/${Platform.current().snykWrapperFileName}"
}
