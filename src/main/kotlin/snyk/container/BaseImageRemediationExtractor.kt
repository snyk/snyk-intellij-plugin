package snyk.container

object BaseImageRemediationExtractor {

    fun extractImageInfo(text: String): BaseImageInfo {
        val lines = text.split("\n")
        // first line is headers
        val parts = lines
            .filter { s -> s.trim().isNotEmpty() }
            .first { s -> !s.startsWith("Base Image    Vulnerabilities  Severity") }
            .split("  ")
            .filter { s -> s.trim().isNotEmpty() }

        val name = parts[0]
        var critical = 0
        var high = 0
        var medium = 0
        var low = 0

        val vulnerabilities = parts[2]
        val chunks = vulnerabilities.split(", ")
        for (chunk in chunks) {
            if (chunk.contains("critical")) {
                critical = extractIssueCount(chunk)
            }
            if (chunk.contains("high")) {
                high = extractIssueCount(chunk)
            }
            if (chunk.contains("medium")) {
                medium = extractIssueCount(chunk)
            }
            if (chunk.contains("low")) {
                low = extractIssueCount(chunk)
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

    private fun extractIssueCount(chunk: String) = chunk.trim().split(" ")[0].toInt()
}
