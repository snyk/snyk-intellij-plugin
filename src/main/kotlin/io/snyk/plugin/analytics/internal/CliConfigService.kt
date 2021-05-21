package io.snyk.plugin.analytics.internal

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET

/**
 * An endpoint to get information about CLI configuration like SAST etc.
 */
interface CliConfigService {
    @GET("cli-config/settings/sast")
    fun sast(): Call<CliConfigSettings>
}

data class CliConfigSettings(
    @SerializedName("sastEnabled")
    val sastEnabled: Boolean
)
