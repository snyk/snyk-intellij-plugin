@file:Suppress("unused")

package snyk.common.lsp.settings

import com.google.gson.annotations.SerializedName
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.AuthenticationType
import org.apache.commons.lang3.SystemUtils
import snyk.common.lsp.FolderConfig
import snyk.pluginInfo

data class LanguageServerSettings(
    @SerializedName("activateSnykOpenSource") val activateSnykOpenSource: String? = "false",
    @SerializedName("activateSnykCode") val activateSnykCode: String? = "false",
    @SerializedName("activateSnykIac") val activateSnykIac: String? = "false",
    @SerializedName("insecure") val insecure: String?,
    @SerializedName("endpoint") val endpoint: String?,
    @SerializedName("additionalParams") val additionalParams: String? = null,
    @SerializedName("additionalEnv") val additionalEnv: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("sendErrorReports") val sendErrorReports: String? = "true",
    @SerializedName("organization") val organization: String? = null,
    @SerializedName("enableTelemetry") val enableTelemetry: String? = "false",
    @SerializedName("manageBinariesAutomatically") val manageBinariesAutomatically: String? = "false",
    @SerializedName("cliPath") val cliPath: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("integrationName") val integrationName: String? = pluginInfo.integrationName,
    @SerializedName("integrationVersion") val integrationVersion: String? = pluginInfo.integrationVersion,
    @SerializedName("automaticAuthentication") val automaticAuthentication: String? = "false",
    @SerializedName("deviceId") val deviceId: String? = pluginSettings().userAnonymousId,
    @SerializedName("filterSeverity") val filterSeverity: SeverityFilter? = null,
    @SerializedName("issueViewOptions") val issueViewOptions: IssueViewOptions? = null,
    @SerializedName("enableTrustedFoldersFeature") val enableTrustedFoldersFeature: String? = "false",
    @SerializedName("trustedFolders") val trustedFolders: List<String>? = emptyList(),
    @SerializedName("activateSnykCodeSecurity") val activateSnykCodeSecurity: String? = "false",
    @SerializedName("osPlatform") val osPlatform: String? = SystemUtils.OS_NAME,
    @SerializedName("osArch") val osArch: String? = SystemUtils.OS_ARCH,
    @SerializedName("runtimeVersion") val runtimeVersion: String? = SystemUtils.JAVA_VERSION,
    @SerializedName("runtimeName") val runtimeName: String? = SystemUtils.JAVA_RUNTIME_NAME,
    @SerializedName("scanningMode") val scanningMode: String? = null,
    @SerializedName("authenticationMethod") val authenticationMethod: String = AuthenticationType.OAUTH2.languageServerSettingsName,
    @SerializedName("snykCodeApi") val snykCodeApi: String? = null,
    @SerializedName("enableSnykLearnCodeActions") val enableSnykLearnCodeActions: String? = null,
    @SerializedName("enableSnykOSSQuickFixCodeActions") val enableSnykOSSQuickFixCodeActions: String? = null,
    @SerializedName("requiredProtocolVersion") val requiredProtocolVersion: String =
        pluginSettings().requiredLsProtocolVersion.toString(),
    @SerializedName("hoverVerbosity") val hoverVerbosity: Int = 0,
    @SerializedName("outputFormat") val outputFormat: String = "html",
    @SerializedName("enableDeltaFindings") val enableDeltaFindings: String = pluginSettings().isDeltaFindingsEnabled().toString(),
    @SerializedName("folderConfigs") val folderConfigs: List<FolderConfig> = emptyList()
)

data class SeverityFilter(
    @SerializedName("critical") val critical: Boolean?,
    @SerializedName("high") val high: Boolean?,
    @SerializedName("medium") val medium: Boolean?,
    @SerializedName("low") val low: Boolean?,
)

data class IssueViewOptions(
    @SerializedName("openIssues") val openIssues: Boolean?,
    @SerializedName("ignoredIssues") val ignoredIssues: Boolean?,
)
