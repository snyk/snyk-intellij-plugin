package snyk.container

class ContainerIssuesForFile {
    lateinit var vulnerabilities: Array<ContainerIssue>
    lateinit var targetFile: String
    lateinit var projectName: String

    lateinit var imageName: String
    lateinit var lineNumber: Number

    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size
}

class ContainerIssue {
    lateinit var id: String
    lateinit var title: String
    lateinit var severity: String

    lateinit var packageManager: String
}
