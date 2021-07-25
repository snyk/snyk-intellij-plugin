package snyk.amplitude.api

import com.google.gson.annotations.SerializedName

class ExperimentUser(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("platform")
    val platform: String = "JetBrains"
) {
    override fun toString(): String {
        return "ExperimentUser(userId=$userId, platform=$platform)"
    }
}
