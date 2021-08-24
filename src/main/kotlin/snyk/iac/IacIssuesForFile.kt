package snyk.iac

class IacIssuesForFile {
    lateinit var infrastructureAsCodeIssues: Array<IacIssue>
    lateinit var targetFile: String
    lateinit var packageManager: String

    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size
}

class IacIssue {
    lateinit var id: String
    lateinit var title: String
    lateinit var severity: String

    lateinit var publicId: String
    lateinit var documentation: String
    lateinit var lineNumber: Integer

    lateinit var issue: String
    lateinit var impact: String
    var resolve: String? = null

    lateinit var references: List<String>
    lateinit var path: List<String>
}
