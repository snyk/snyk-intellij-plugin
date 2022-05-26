package snyk.container

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import io.snyk.plugin.Severity

data class ContainerIssuesForImage(
    val vulnerabilities: List<ContainerIssue>,
    val projectName: String,
    val docker: Docker,
    val error: String?,
    @SerializedName("path") val imageName: String,
    @Expose val baseImageRemediationInfo: BaseImageRemediationInfo? = null,
    @Expose val workloadImages: List<KubernetesWorkloadImage> = emptyList()
) {
    val obsolete: Boolean get() = vulnerabilities.any { it.obsolete }
    val ignored: Boolean get() = vulnerabilities.all { it.ignored }
    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size

    fun getSeverity() = vulnerabilities.map { it.getSeverity() }.max() ?: Severity.UNKNOWN
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
    private val severity: String,
    val identifiers: Identifiers? = null,
    val cvssScore: String? = null,
    @SerializedName("CVSSv3") val cvssV3: String? = null,
    val nearestFixedInVersion: String? = null,
    val from: List<String>,
    val packageManager: String,
    @Expose val obsolete: Boolean = false,
    @Expose val ignored: Boolean = false
) {
    fun getSeverity(): Severity = Severity.getFromName(severity)
}

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
