package io.snyk.plugin.services.download

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import io.snyk.plugin.cli.Platform
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

class SnykDownloader {
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

    fun verifyCLIChecksum(file: File) {
        if (file.exists()) {
            val remoteShaUrl =
                URL(String.format(SHA256_DOWNLOAD_URL, Platform.current().snykWrapperFileName))
            val sha256 = calculateSha256(file.readBytes())
            val remoteSha256 = remoteShaUrl.readText(Charsets.UTF_8).toLowerCase()
            if (sha256 != remoteSha256.split(" ")[0]) {
                throw ChecksumVerificationException("Expected $remoteSha256, but downloaded file has $sha256")
            }
        }
    }

    fun downloadFile(cliFile: File, indicator: ProgressIndicator?): File {
        val snykWrapperFileName = Platform.current().snykWrapperFileName
        val url = URL(java.lang.String.format(LATEST_RELEASE_DOWNLOAD_URL, snykWrapperFileName)).toString()

        if (cliFile.exists()) {
            cliFile.delete()
        }

        HttpRequests
            .request(url)
            .productNameAsUserAgent()
            .saveToFile(cliFile, indicator)

        verifyCLIChecksum(cliFile)
        cliFile.setExecutable(true)
        return cliFile
    }
}
