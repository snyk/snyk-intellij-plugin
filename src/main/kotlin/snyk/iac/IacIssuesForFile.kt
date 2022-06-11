package snyk.iac

data class IacIssuesForFile(
    val infrastructureAsCodeIssues: List<IacIssue>,
    val targetFile: String,
    val targetFilePath: String,
    val packageManager: String
) {
    val obsolete: Boolean get() = infrastructureAsCodeIssues.any { it.obsolete }
    val ignored: Boolean get() = infrastructureAsCodeIssues.all { it.ignored }
    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size
}

/* Real json Example: src/integTest/resources/iac-test-results/infrastructure-as-code-goof.json */
