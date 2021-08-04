package io.snyk.plugin.cli

import com.google.gson.annotations.SerializedName

data class CliError(
    @SerializedName("error")
    val message: String,

    val path: String
)
