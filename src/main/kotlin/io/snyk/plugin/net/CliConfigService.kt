package io.snyk.plugin.net

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * An endpoint to get information about CLI configuration like SAST etc.
 */
interface CliConfigService {
    @GET(apiName)
    fun sast(@Query("org") org: String? = null): Call<CliConfigSettings>

    companion object {
        const val apiName = "cli-config/settings/sast"
    }
}

data class CliConfigSettings(
    @SerializedName("sastEnabled")
    val sastEnabled: Boolean,

    @SerializedName("localCodeEngine")
    val localCodeEngine: LocalCodeEngine,

    @SerializedName("reportFalsePositivesEnabled")
    val reportFalsePositivesEnabled: Boolean
)

data class CliConfigSettingsError(
    @SerializedName("userMessage")
    val userMessage: String,

    @SerializedName("code")
    val code: Int?
)

/**
 * SAST local code engine configuration.
 */
data class LocalCodeEngine(
    @SerializedName("enabled")
    val enabled: Boolean,

    @SerializedName("url")
    val url: String,

    @SerializedName("allowCloudUpload")
    val allowCloudUpload: Boolean
)
