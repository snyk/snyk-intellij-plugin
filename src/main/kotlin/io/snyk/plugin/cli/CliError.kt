package io.snyk.plugin.cli

import com.google.gson.annotations.SerializedName

class CliError(
    @SerializedName("ok") val isSuccess: Boolean,
    val error: String,
    val path: String) {

}
