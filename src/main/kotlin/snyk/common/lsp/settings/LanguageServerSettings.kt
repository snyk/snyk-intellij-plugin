package snyk.common.lsp.settings

import com.google.gson.annotations.SerializedName

data class IssueViewOptions(
  @SerializedName("openIssues") val openIssues: Boolean?,
  @SerializedName("ignoredIssues") val ignoredIssues: Boolean?,
)

data class ConfigSetting(
  @SerializedName("value") val value: Any? = null,
  @SerializedName("changed") val changed: Boolean? = null,
  @SerializedName("source") val source: String? = null,
  @SerializedName("originScope") val originScope: String? = null,
  @SerializedName("isLocked") val isLocked: Boolean? = null,
)

data class LspFolderConfig(
  @SerializedName("folderPath") val folderPath: String,
  @SerializedName("settings") val settings: Map<String, ConfigSetting>? = null,
)

fun LspFolderConfig.withSetting(
  key: String,
  value: Any?,
  changed: Boolean? = null,
): LspFolderConfig {
  val newSettings = (settings ?: emptyMap()).toMutableMap()
  val existing = newSettings[key]
  newSettings[key] =
    ConfigSetting(
      value = value,
      changed = changed ?: existing?.changed,
      source = existing?.source,
      originScope = existing?.originScope,
      isLocked = existing?.isLocked,
    )
  return copy(settings = newSettings)
}

data class LspConfigurationParam(
  @SerializedName("settings") val settings: Map<String, ConfigSetting>? = null,
  @SerializedName("folderConfigs") val folderConfigs: List<LspFolderConfig>? = null,
)

data class InitializationOptions(
  @SerializedName("settings") val settings: Map<String, ConfigSetting>? = null,
  @SerializedName("folderConfigs") val folderConfigs: List<LspFolderConfig>? = null,
  @SerializedName("requiredProtocolVersion") val requiredProtocolVersion: String? = null,
  @SerializedName("deviceId") val deviceId: String? = null,
  @SerializedName("integrationName") val integrationName: String? = null,
  @SerializedName("integrationVersion") val integrationVersion: String? = null,
  @SerializedName("osPlatform") val osPlatform: String? = null,
  @SerializedName("osArch") val osArch: String? = null,
  @SerializedName("runtimeVersion") val runtimeVersion: String? = null,
  @SerializedName("runtimeName") val runtimeName: String? = null,
  @SerializedName("hoverVerbosity") val hoverVerbosity: Int? = null,
  @SerializedName("outputFormat") val outputFormat: String? = null,
  @SerializedName("path") val path: String? = null,
  @SerializedName("trustedFolders") val trustedFolders: List<String>? = emptyList(),
)
