package io.snyk.plugin

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.snyk.plugin.services.SnykProjectSettingsStateService
import java.time.Instant
import java.util.Date
import java.util.UUID

fun setupDummyCliFile() {
    val cliFile = getCliFile()

    if (!cliFile.exists()) {
        if (!cliFile.parentFile.exists()) cliFile.mkdirs()
        cliFile.createNewFile()
    }
}

fun removeDummyCliFile() {
    val cliFile = getCliFile()
    if (cliFile.exists()) {
        cliFile.delete()
    }
}

fun resetSettings(project: Project?) {
    pluginSettings().apply {
        token = null
        customEndpointUrl = null
        organization = null
        ignoreUnknownCA = false
        cliVersion = null

        cliScanEnable = true

        ossScanEnable = true
        advisorEnable = true
        snykCodeSecurityIssuesScanEnable = true
        snykCodeQualityIssuesScanEnable = false
        iacScanEnabled = false

        sastOnServerEnabled = null
        usageAnalyticsEnabled = true
        crashReportingEnabled = true

        lowSeverityEnabled = true
        mediumSeverityEnabled = true
        highSeverityEnabled = true
        criticalSeverityEnabled = true

        lastCheckDate = null
        pluginFirstRun = true
        pluginInstalled = false

        pluginFirstInstallTime = Date.from(Instant.now())
        lastTimeFeedbackRequestShown = Date.from(Instant.now())
        showFeedbackRequest = true

        scanningReminderWasShown = false

        userAnonymousId = UUID.randomUUID().toString()
    }

    project?.service<SnykProjectSettingsStateService>()?.additionalParameters = null
}
