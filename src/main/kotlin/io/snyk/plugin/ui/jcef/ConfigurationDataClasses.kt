package io.snyk.plugin.ui.jcef

import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Type adapter that handles additionalParameters as either a string or an array of strings. The LS
 * HTML may send it as a string, while the fallback HTML sends it as an array.
 */
class StringOrListTypeAdapter : TypeAdapter<List<String>?>() {
  override fun write(out: JsonWriter, value: List<String>?) {
    if (value == null) {
      out.nullValue()
    } else {
      out.beginArray()
      value.forEach { out.value(it) }
      out.endArray()
    }
  }

  override fun read(reader: JsonReader): List<String>? =
    when (reader.peek()) {
      JsonToken.NULL -> {
        reader.nextNull()
        null
      }
      JsonToken.STRING -> {
        val str = reader.nextString()
        if (str.isNullOrBlank()) emptyList() else listOf(str)
      }
      JsonToken.BEGIN_ARRAY -> {
        val list = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
          if (reader.peek() == JsonToken.STRING) {
            list.add(reader.nextString())
          } else {
            reader.skipValue()
          }
        }
        reader.endArray()
        list
      }
      else -> {
        reader.skipValue()
        null
      }
    }
}

data class SaveConfigRequest(
  // Scan Settings — primary keys match LS configuration HTML/JS; alternates keep tests & fallback
  // HTML working
  @SerializedName(value = "snyk_oss_enabled", alternate = ["activateSnykOpenSource"])
  val activateSnykOpenSource: Boolean? = null,
  @SerializedName(value = "snyk_code_enabled", alternate = ["activateSnykCode"])
  val activateSnykCode: Boolean? = null,
  @SerializedName(value = "snyk_iac_enabled", alternate = ["activateSnykIac"])
  val activateSnykIac: Boolean? = null,
  @SerializedName(value = "snyk_secrets_enabled", alternate = ["activateSnykSecrets"])
  val activateSnykSecrets: Boolean? = null,
  @SerializedName(value = "scan_automatic", alternate = ["scanningMode"])
  val scanningMode: String? = null,

  // Connection Settings
  @SerializedName("organization") val organization: String? = null,
  @SerializedName(value = "api_endpoint", alternate = ["endpoint"]) val endpoint: String? = null,
  @SerializedName("token") val token: String? = null,
  @SerializedName(value = "proxy_insecure", alternate = ["insecure"]) val insecure: Boolean? = null,

  // Authentication
  @SerializedName(value = "authentication_method", alternate = ["authenticationMethod"])
  val authenticationMethod: String? = null,

  // Severity Filters
  @SerializedName(value = "severity_filter_critical", alternate = ["filterSeverityCritical"])
  val severityFilterCritical: Boolean? = null,
  @SerializedName(value = "severity_filter_high", alternate = ["filterSeverityHigh"])
  val severityFilterHigh: Boolean? = null,
  @SerializedName(value = "severity_filter_medium", alternate = ["filterSeverityMedium"])
  val severityFilterMedium: Boolean? = null,
  @SerializedName(value = "severity_filter_low", alternate = ["filterSeverityLow"])
  val severityFilterLow: Boolean? = null,
  @SerializedName("issue_view_open_issues") val issueViewOpenIssues: Boolean? = null,
  @SerializedName("issue_view_ignored_issues") val issueViewIgnoredIssues: Boolean? = null,
  @SerializedName(value = "scan_net_new", alternate = ["enableDeltaFindings"])
  val enableDeltaFindings: Boolean? = null,
  @SerializedName(value = "risk_score_threshold", alternate = ["riskScoreThreshold"])
  val riskScoreThreshold: Int? = null,

  // CLI Settings
  @SerializedName(value = "cli_path", alternate = ["cliPath"]) val cliPath: String? = null,
  @SerializedName(value = "automatic_download", alternate = ["manageBinariesAutomatically"])
  val manageBinariesAutomatically: Boolean? = null,
  @SerializedName(value = "binary_base_url", alternate = ["cliBaseDownloadURL"])
  val cliBaseDownloadURL: String? = null,
  @SerializedName(value = "cli_release_channel", alternate = ["cliReleaseChannel"])
  val cliReleaseChannel: String? = null,

  // Trusted Folders
  @SerializedName(value = "trusted_folders", alternate = ["trustedFolders"])
  val trustedFolders: List<String>? = null,

  // Folder Configs
  @SerializedName("folderConfigs") val folderConfigs: List<FolderConfigData>? = null,

  // Form Type Indicator
  @SerializedName("isFallbackForm") val isFallbackForm: Boolean? = null,
)

data class FolderConfigData(
  @SerializedName("folderPath") val folderPath: String,
  @SerializedName(value = "additional_parameters", alternate = ["additionalParameters"])
  @com.google.gson.annotations.JsonAdapter(StringOrListTypeAdapter::class)
  val additionalParameters: List<String>? = null,
  @SerializedName(value = "additional_environment", alternate = ["additionalEnv"])
  val additionalEnv: String? = null,
  @SerializedName(value = "preferred_org", alternate = ["preferredOrg"])
  val preferredOrg: String? = null,
  @SerializedName("autoDeterminedOrg") val autoDeterminedOrg: String? = null,
  @SerializedName(value = "org_set_by_user", alternate = ["orgSetByUser"])
  val orgSetByUser: Boolean? = null,
  @SerializedName(value = "scan_command_config", alternate = ["scanCommandConfig"])
  val scanCommandConfig: Map<String, ScanCommandConfigData>? = null,
  // Org-scope override fields (LS sends snake_case; alternates accept camelCase)
  @SerializedName(value = "scan_automatic", alternate = ["scanAutomatic"])
  val scanAutomatic: Boolean? = null,
  @SerializedName(value = "scan_net_new", alternate = ["scanNetNew"])
  val scanNetNew: Boolean? = null,
  @SerializedName(value = "severity_filter_critical", alternate = ["severityFilterCritical"])
  val severityFilterCritical: Boolean? = null,
  @SerializedName(value = "severity_filter_high", alternate = ["severityFilterHigh"])
  val severityFilterHigh: Boolean? = null,
  @SerializedName(value = "severity_filter_medium", alternate = ["severityFilterMedium"])
  val severityFilterMedium: Boolean? = null,
  @SerializedName(value = "severity_filter_low", alternate = ["severityFilterLow"])
  val severityFilterLow: Boolean? = null,
  @SerializedName(value = "snyk_oss_enabled", alternate = ["snykOssEnabled"])
  val snykOssEnabled: Boolean? = null,
  @SerializedName(value = "snyk_code_enabled", alternate = ["snykCodeEnabled"])
  val snykCodeEnabled: Boolean? = null,
  @SerializedName(value = "snyk_iac_enabled", alternate = ["snykIacEnabled"])
  val snykIacEnabled: Boolean? = null,
  @SerializedName(value = "snyk_secrets_enabled", alternate = ["snykSecretsEnabled"])
  val snykSecretsEnabled: Boolean? = null,
  @SerializedName(value = "issue_view_open_issues", alternate = ["issueViewOpenIssues"])
  val issueViewOpenIssues: Boolean? = null,
  @SerializedName(value = "issue_view_ignored_issues", alternate = ["issueViewIgnoredIssues"])
  val issueViewIgnoredIssues: Boolean? = null,
  @SerializedName(value = "risk_score_threshold", alternate = ["riskScoreThreshold"])
  val riskScoreThreshold: Int? = null,
)

data class ScanCommandConfigData(
  @SerializedName("preScanCommand") val preScanCommand: String? = null,
  @SerializedName("preScanOnlyReferenceFolder") val preScanOnlyReferenceFolder: Boolean? = null,
  @SerializedName("postScanCommand") val postScanCommand: String? = null,
  @SerializedName("postScanOnlyReferenceFolder") val postScanOnlyReferenceFolder: Boolean? = null,
)
