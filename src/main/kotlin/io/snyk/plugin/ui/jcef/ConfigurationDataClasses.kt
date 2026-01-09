package io.snyk.plugin.ui.jcef

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class SaveConfigRequest(
    // Scan Settings
    @SerializedName("activateSnykOpenSource") val activateSnykOpenSource: Boolean? = null,
    @SerializedName("activateSnykCode") val activateSnykCode: Boolean? = null,
    @SerializedName("activateSnykIac") val activateSnykIac: Boolean? = null,
    @SerializedName("scanningMode") val scanningMode: String? = null,

    // Connection Settings
    @SerializedName("organization") val organization: String? = null,
    @SerializedName("endpoint") val endpoint: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("insecure") val insecure: Boolean? = null,

    // Authentication
    @SerializedName("authenticationMethod") val authenticationMethod: String? = null,

    // Filters
    @SerializedName("filterSeverity") val filterSeverity: SeverityFilterConfig? = null,
    @SerializedName("issueViewOptions") val issueViewOptions: IssueViewOptionsConfig? = null,
    @SerializedName("enableDeltaFindings") val enableDeltaFindings: Boolean? = null,
    @SerializedName("riskScoreThreshold") val riskScoreThreshold: Int? = null,

    // CLI Settings
    @SerializedName("cliPath") val cliPath: String? = null,
    @SerializedName("manageBinariesAutomatically") val manageBinariesAutomatically: Boolean? = null,
    @SerializedName("cliBaseDownloadURL") val cliBaseDownloadURL: String? = null,
    @SerializedName("cliReleaseChannel") val cliReleaseChannel: String? = null,

    // Trusted Folders
    @SerializedName("trustedFolders") val trustedFolders: List<String>? = null,

    // Folder Configs
    @SerializedName("folderConfigs") val folderConfigs: List<FolderConfigData>? = null,

    // Form Type Indicator
    @SerializedName("isFallbackForm") val isFallbackForm: Boolean? = null
)

data class SeverityFilterConfig(
    @SerializedName("critical") val critical: Boolean? = null,
    @SerializedName("high") val high: Boolean? = null,
    @SerializedName("medium") val medium: Boolean? = null,
    @SerializedName("low") val low: Boolean? = null
)

data class IssueViewOptionsConfig(
    @SerializedName("openIssues") val openIssues: Boolean? = null,
    @SerializedName("ignoredIssues") val ignoredIssues: Boolean? = null
)

data class FolderConfigData(
    @SerializedName("folderPath") val folderPath: String,
    @SerializedName("additionalParameters") val additionalParameters: List<String>? = null,
    @SerializedName("additionalEnv") val additionalEnv: String? = null,
    @SerializedName("preferredOrg") val preferredOrg: String? = null,
    @SerializedName("autoDeterminedOrg") val autoDeterminedOrg: String? = null,
    @SerializedName("orgSetByUser") val orgSetByUser: Boolean? = null,
    @SerializedName("scanCommandConfig") val scanCommandConfig: Map<String, ScanCommandConfigData>? = null
)

data class ScanCommandConfigData(
    @SerializedName("preScanCommand") val preScanCommand: String? = null,
    @SerializedName("preScanOnlyReferenceFolder") val preScanOnlyReferenceFolder: Boolean? = null,
    @SerializedName("postScanCommand") val postScanCommand: String? = null,
    @SerializedName("postScanOnlyReferenceFolder") val postScanOnlyReferenceFolder: Boolean? = null
)
