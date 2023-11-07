package snyk.common.lsp.commands

import com.google.gson.annotations.SerializedName
import java.util.Date

data class ScanDoneEvent(
    @SerializedName("data") val data: Data
) {
    data class Data(
        @SerializedName("type") val type: String,
        @SerializedName("attributes") val attributes: Attributes
    )

    data class Attributes(
        @SerializedName("deviceId") val deviceId: String,
        @SerializedName("application") val application: String,
        @SerializedName("application_version") val applicationVersion: String,
        @SerializedName("os") val os: String,
        @SerializedName("arch") val arch: String,
        @SerializedName("integration_name") val integrationName: String,
        @SerializedName("integration_version") val integrationVersion: String,
        @SerializedName("integration_environment") val integrationEnvironment: String,
        @SerializedName("integration_environment_version") val integrationEnvironmentVersion: String,
        @SerializedName("event_type") val eventType: String,
        @SerializedName("status") val status: String,
        @SerializedName("scan_type") val scanType: String,
        @SerializedName("unique_issue_count") val uniqueIssueCount: UniqueIssueCount,
        @SerializedName("duration_ms") val durationMs: String,
        @SerializedName("timestamp_finished") val timestampFinished: Date
    )

    data class UniqueIssueCount(
        @SerializedName("critical") val critical: Int,
        @SerializedName("high") val high: Int,
        @SerializedName("medium") val medium: Int,
        @SerializedName("low") val low: Int
    )
}
