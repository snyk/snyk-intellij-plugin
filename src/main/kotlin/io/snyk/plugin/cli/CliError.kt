package io.snyk.plugin.cli

import com.google.gson.annotations.SerializedName

class CliError(
    @SerializedName("ok") val isSuccess: Boolean,
    @SerializedName("error") val message: String,
    val path: String) {

}
