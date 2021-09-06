package snyk.container

import com.google.gson.annotations.SerializedName

class ContainerIssuesForFile {
    lateinit var vulnerabilities: Array<ContainerIssue>
    lateinit var targetFile: String
    lateinit var projectName: String
    lateinit var docker: Docker

    lateinit var imageName: String
    lateinit var lineNumber: Number
    var baseImageRemediationInfo: BaseImageRemediationInfo? = null

    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size
}

class Docker {
    lateinit var baseImageRemediation: BaseImageRemediation
}

class BaseImageRemediation {
    lateinit var code: String
    lateinit var advice: Array<Advice>

    fun isRemediationAvailable(): Boolean = code == "REMEDIATION_AVAILABLE"
}

class Advice {
    lateinit var message: String
    var bold: Boolean? = null
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

data class BaseImageRemediationInfo(
    val currentImage: BaseImageInfo?,
    val majorUpgrades: BaseImageInfo?,
    val minorUpgrades: BaseImageInfo?,
    val alternativeUpgrades: BaseImageInfo?
)

data class BaseImageInfo(
    val name: String,
    val vulnerabilities: BaseImageVulnerabilities
)

data class BaseImageVulnerabilities(
    val critical: Int,
    val high: Int,
    val medium: Int,
    val low: Int
)
