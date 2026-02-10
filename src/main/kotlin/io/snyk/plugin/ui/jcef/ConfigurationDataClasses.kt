package io.snyk.plugin.ui.jcef

import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import snyk.common.lsp.SnykConfigurationParam
import snyk.common.lsp.settings.IssueViewOptions
import snyk.common.lsp.settings.SeverityFilter

/**
 * Type adapter that handles additionalParameters as either a string or an array of strings.
 * The LS HTML may send it as a string, while the fallback HTML sends it as an array.
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

    override fun read(reader: JsonReader): List<String>? {
        return when (reader.peek()) {
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
}

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
    @SerializedName("filterSeverity") val filterSeverity: SeverityFilter? = null,
    @SerializedName("issueViewOptions") val issueViewOptions: IssueViewOptions? = null,
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
) {
    fun toSnykConfigurationParam(isFallback: Boolean): SnykConfigurationParam {
        if (isFallback) {
            return SnykConfigurationParam(
                manageBinariesAutomatically = manageBinariesAutomatically?.toString(),
                insecure = insecure?.toString(),
            )
        }
        return SnykConfigurationParam(
            token = token,
            endpoint = endpoint,
            organization = organization,
            authenticationMethod = authenticationMethod,
            manageBinariesAutomatically = manageBinariesAutomatically?.toString(),
            activateSnykOpenSource = (activateSnykOpenSource ?: false).toString(),
            activateSnykCodeSecurity = (activateSnykCode ?: false).toString(),
            activateSnykIac = (activateSnykIac ?: false).toString(),
            scanningMode = scanningMode,
            insecure = insecure?.toString(),
            riskScoreThreshold = riskScoreThreshold,
            enableDeltaFindings = enableDeltaFindings?.toString(),
            filterSeverity = filterSeverity,
            issueViewOptions = issueViewOptions,
        )
    }
}

data class FolderConfigData(
    @SerializedName("folderPath") val folderPath: String,
    @SerializedName("additionalParameters")
    @com.google.gson.annotations.JsonAdapter(StringOrListTypeAdapter::class)
    val additionalParameters: List<String>? = null,
    @SerializedName("additionalEnv") val additionalEnv: String? = null,
    @SerializedName("preferredOrg") val preferredOrg: String? = null,
    @SerializedName("autoDeterminedOrg") val autoDeterminedOrg: String? = null,
    @SerializedName("orgSetByUser") val orgSetByUser: Boolean? = null,
    @SerializedName("scanCommandConfig") val scanCommandConfig: Map<String, ScanCommandConfigData>? = null
)

// Extracts all fields except folderPath from a raw folder config JSON entry.
// folderPath is excluded because it is passed separately to sendFolderConfigPatch,
// which prepends it to the map as the identifier for the target folder.
fun extractFolderConfigPatch(rawEntry: JsonObject): Map<String, Any?> {
    val patch = mutableMapOf<String, Any?>()
    for ((key, element) in rawEntry.entrySet()) {
        if (key == "folderPath") continue
        patch[key] = jsonElementToValue(element)
    }
    return patch
}

private fun jsonElementToValue(element: com.google.gson.JsonElement): Any? {
    return when {
        element.isJsonNull -> null
        element.isJsonObject -> {
            val map = mutableMapOf<String, Any?>()
            for ((key, value) in element.asJsonObject.entrySet()) {
                map[key] = jsonElementToValue(value)
            }
            map
        }
        element.isJsonArray -> {
            element.asJsonArray.map { jsonElementToValue(it) }
        }
        element.isJsonPrimitive -> {
            val prim = element.asJsonPrimitive
            when {
                prim.isBoolean -> prim.asBoolean
                prim.isNumber -> prim.asNumber
                else -> prim.asString
            }
        }
        else -> element
    }
}

data class ScanCommandConfigData(
    @SerializedName("preScanCommand") val preScanCommand: String? = null,
    @SerializedName("preScanOnlyReferenceFolder") val preScanOnlyReferenceFolder: Boolean? = null,
    @SerializedName("postScanCommand") val postScanCommand: String? = null,
    @SerializedName("postScanOnlyReferenceFolder") val postScanOnlyReferenceFolder: Boolean? = null
)
