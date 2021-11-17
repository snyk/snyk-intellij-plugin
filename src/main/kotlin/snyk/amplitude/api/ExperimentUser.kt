package snyk.amplitude.api

import com.google.gson.annotations.SerializedName
import io.snyk.plugin.pluginSettings

class ExperimentUser(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("device_id")
    val deviceId: String = pluginSettings().userAnonymousId,

    @SerializedName("platform")
    val platform: String = "JetBrains"
) {
    override fun toString(): String {
        return "ExperimentUser(userId=$userId, platform=$platform)"
    }
}
