@file:Suppress("unused")

package snyk.common.lsp

import com.google.gson.annotations.SerializedName
import io.snyk.plugin.pluginSettings
import org.apache.commons.lang.SystemUtils
import snyk.pluginInfo

data class Settings(
    @SerializedName("activateSnykOpenSource") val activateSnykOpenSource: String? = "false",
    @SerializedName("activateSnykCode") val activateSnykCode: String? = "false",
    @SerializedName("activateSnykIac") val activateSnykIac: String? = "false",
    @SerializedName("insecure") val insecure: String?,
    @SerializedName("endpoint") val endpoint: String?,
    @SerializedName("additionalParams") val additionalParams: String? = null,
    @SerializedName("additionalEnv") val additionalEnv: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("sendErrorReports") val sendErrorReports: String? = "false",
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
    @SerializedName("enableTrustedFoldersFeature") val enableTrustedFoldersFeature: String? = "false",
    @SerializedName("trustedFolders") val trustedFolders: List<String>? = emptyList(),
    @SerializedName("activateSnykCodeSecurity") val activateSnykCodeSecurity: String? = "false",
    @SerializedName("activateSnykCodeQuality") val activateSnykCodeQuality: String? = "false",
    @SerializedName("osPlatform") val osPlatform: String? = SystemUtils.OS_NAME,
    @SerializedName("osArch") val osArch: String? = SystemUtils.OS_ARCH,
    @SerializedName("runtimeVersion") val runtimeVersion: String? = SystemUtils.JAVA_VERSION,
    @SerializedName("runtimeName") val runtimeName: String? = SystemUtils.JAVA_RUNTIME_NAME,
    @SerializedName("scanningMode") val scanningMode: String? = null,
    @SerializedName("authenticationMethod") val authenticationMethod: AuthenticationMethod? = null,
    @SerializedName("snykCodeApi") val snykCodeApi: String? = null,
    @SerializedName("enableSnykLearnCodeActions") val enableSnykLearnCodeActions: String? = null,
    @SerializedName("enableAnalytics") val enableAnalytics: Boolean = false // TODO: enable when service ready
)

data class SeverityFilter(
    @SerializedName("critical") val critical: Boolean?,
    @SerializedName("high") val high: Boolean?,
    @SerializedName("medium") val medium: Boolean?,
    @SerializedName("low") val low: Boolean?
)

enum class AuthenticationMethod {
    @SerializedName("token")
    TokenAuthentication,

    @SerializedName("oauth")
    OAuthAuthentication
}
