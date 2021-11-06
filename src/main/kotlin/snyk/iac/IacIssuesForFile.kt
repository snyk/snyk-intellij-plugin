package snyk.iac

class IacIssuesForFile {
    lateinit var infrastructureAsCodeIssues: Array<IacIssue>
    lateinit var targetFile: String
    lateinit var packageManager: String

    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size
}

/* Real json Example: src/integTest/resources/iac-test-results/infrastructure-as-code-goof.json */
