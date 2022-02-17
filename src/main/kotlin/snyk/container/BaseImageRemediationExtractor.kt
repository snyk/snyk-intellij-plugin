package snyk.container

import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.errorHandler.SentryErrorReporter

object BaseImageRemediationExtractor {
    val regex = "([a-zA-Z0-9:_/.-])+".toRegex()

    fun extractImageInfo(text: String): BaseImageInfo? {
        try {
            val lines = text.split("\n").filter { it.isNotBlank() }
            if (lines.size < 2) return null
            val matches = regex.find(lines[1]) ?: return null

            val name = matches.value
            var next = matches.next()?.next()
            val critical = next?.value?.toInt() ?: -1
            next = next?.next()?.next()
            val high = next?.value?.toInt() ?: -1
            next = next?.next()?.next()
            val medium = next?.value?.toInt() ?: -1
            next = next?.next()?.next()
            val low = next?.value?.toInt() ?: -1

            if (name.isBlank() || critical < 0 || high < 0 || medium < 0 || low < 0) return null

            return BaseImageInfo(
                name = name,
                vulnerabilities = BaseImageVulnerabilities(
                    critical = critical,
                    high = high,
                    medium = medium,
                    low = low
                )
            )
        } catch (e: NumberFormatException) {
            SnykBalloonNotificationHelper.showError("Unexpected format while parsing Snyk Container output.")
            SentryErrorReporter.captureException(e)
        }
        return null
    }
}
