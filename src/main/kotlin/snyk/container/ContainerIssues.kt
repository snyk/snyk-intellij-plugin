package snyk.container

class ContainerIssuesForFile {
    lateinit var vulnerabilities: Array<ContainerIssue>
    lateinit var projectName: String

    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size
}

class ContainerIssue {
    lateinit var id: String
    lateinit var title: String
    lateinit var severity: String

    lateinit var packageManager: String
}
