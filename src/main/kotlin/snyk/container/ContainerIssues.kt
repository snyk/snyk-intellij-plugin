package snyk.container

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ContainerIssuesForImage(
    val vulnerabilities: List<ContainerIssue>,
    val projectName: String,
    val docker: Docker,
    val error: String?,
    @SerializedName("path") val imageName: String,
    @Expose val baseImageRemediationInfo: BaseImageRemediationInfo? = null,
    @Expose val workloadImage: KubernetesWorkloadImage? = null
) {
    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size
}

data class Docker(val baseImageRemediation: BaseImageRemediation? = null)

data class BaseImageRemediation(
    val code: String,
    val advice: List<Advice>
) {
    fun isRemediationAvailable() = code == "REMEDIATION_AVAILABLE"
}

data class Advice(val message: String, val bold: Boolean? = null)

data class ContainerIssue(
    val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val identifiers: Identifiers? = null,
    val cvssScore: String? = null,
    var CVSSv3: String? = null,
    val nearestFixedInVersion: String? = null,
    val from: List<String>,
    val packageManager: String
)

data class Identifiers(
    @SerializedName("CWE")
    val cwe: List<String>,

    @SerializedName("CVE")
    val cve: List<String>
)

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
