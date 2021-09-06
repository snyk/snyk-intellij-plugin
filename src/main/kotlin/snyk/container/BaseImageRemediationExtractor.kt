package snyk.container

object BaseImageRemediationExtractor {
    fun extractRemediationInfo(text: String): BaseImageRemediationInfo? {
        val lines = text.split("\\n")
        // first line is headers
        val baseImageLines = lines.drop(1)
        baseImageLines.forEach { baseImageLine ->
            // parts are separated with at least two whitespaces
            extractImageInfo(baseImageLine)
        }

        return null
    }

    fun extractImageInfo(text: String): BaseImageInfo {
        val lines = text.split("\n".toRegex())
        // first line is headers
        val baseImageLines = lines.drop(1)

        // TODO: handle multiple remediation advices
        val parts = baseImageLines[0].split("  +".toRegex())
        val name = parts[0]
        var critical = 0
        var high = 0
        var medium = 0
        var low = 0

        val vulnerabilities = parts[2]
        val chunks = vulnerabilities.split(", ")
        for (chunk in chunks) {
            if (chunk.contains("critical")) {
                critical = extractVulnerabilitiesBySeverity(chunk)
            }
            if (chunk.contains("high")) {
                high = extractVulnerabilitiesBySeverity(chunk)
            }
            if (chunk.contains("medium")) {
                medium = extractVulnerabilitiesBySeverity(chunk)
            }
            if (chunk.contains("low")) {
                low = extractVulnerabilitiesBySeverity(chunk)
            }
        }

        return BaseImageInfo(
            name = name,
            vulnerabilities = BaseImageVulnerabilities(
                critical = critical,
                high = high,
                medium = medium,
                low = low
            )
        )
    }

    private fun extractVulnerabilitiesBySeverity(chunk: String): Int {
        val count = chunk.substringBefore(" ")
        return count.toInt()
    }
}
