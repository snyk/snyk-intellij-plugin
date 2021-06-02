package io.snyk.plugin.net

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET

/**
 * An endpoint to get information about CLI configuration like SAST etc.
 */
interface CliConfigService {
    @GET(apiName)
    fun sast(): Call<CliConfigSettings>

    companion object {
        const val apiName = "cli-config/settings/sast"
    }
}

data class CliConfigSettings(
    @SerializedName("sastEnabled")
    val sastEnabled: Boolean
)
