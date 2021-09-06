package snyk.container

import com.google.gson.annotations.SerializedName

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
    lateinit var description: String
    lateinit var severity: String
    var identifiers: Identifiers? = null
    var cvssScore: String? = null
    var nearestFixedInVersion: String? = null

    lateinit var from: Array<String>
    lateinit var packageManager: String
}

class Identifiers {
    @SerializedName("CWE")
    lateinit var cwe: Array<String>

    @SerializedName("CVE")
    lateinit var cve: Array<String>
}
