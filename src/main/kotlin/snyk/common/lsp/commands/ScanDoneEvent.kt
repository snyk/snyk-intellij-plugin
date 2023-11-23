package snyk.common.lsp.commands

import com.google.gson.annotations.SerializedName
import io.snyk.plugin.getArch
import io.snyk.plugin.getOS
import io.snyk.plugin.pluginSettings
import snyk.pluginInfo
import java.time.ZonedDateTime

data class ScanDoneEvent(
    @SerializedName("data") val data: Data
) {
    data class Data(
        @SerializedName("type") val type: String = "analytics",
        @SerializedName("attributes") val attributes: Attributes
    )

    data class Attributes(
        @SerializedName("device_id") val deviceId: String = pluginSettings().userAnonymousId,
        @SerializedName("application") val application: String = pluginInfo.integrationEnvironment,
        @SerializedName("application_version")
        val applicationVersion: String = pluginInfo.integrationEnvironmentVersion,
        @SerializedName("os") val os: String = getOS(),
        @SerializedName("arch") val arch: String = getArch(),

        @SerializedName("integration_name")
        val integrationName: String = pluginInfo.integrationName,

        @SerializedName("integration_version")
        val integrationVersion: String = pluginInfo.integrationVersion,

        @SerializedName("integration_environment")
        val integrationEnvironment: String = pluginInfo.integrationEnvironment,

        @SerializedName("integration_environment_version")
        val integrationEnvironmentVersion: String = pluginInfo.integrationEnvironmentVersion,

        @SerializedName("event_type")
        val eventType: String = "Scan done",

        @SerializedName("status")
        val status: String = "Succeeded",

        @SerializedName("scan_type")
        val scanType: String,

        @SerializedName("unique_issue_count")
        val uniqueIssueCount: UniqueIssueCount,

        @SerializedName("duration_ms")
        val durationMs: String,

        @SerializedName("timestamp_finished")
        val timestampFinished: String = ZonedDateTime.now().withZoneSameInstant(java.time.ZoneOffset.UTC).toString()
    )

    data class UniqueIssueCount(
        @SerializedName("critical") val critical: Int,
        @SerializedName("high") val high: Int,
        @SerializedName("medium") val medium: Int,
        @SerializedName("low") val low: Int
    )
}
