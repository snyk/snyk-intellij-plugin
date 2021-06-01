package io.snyk.plugin

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.services.SnykCliService
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykCodeService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import java.io.File
import java.net.URL
import java.util.Objects.nonNull

fun getCli(project: Project): SnykCliService = project.service()

fun getSnykCode(project: Project): SnykCodeService = project.service()

fun getCliFile() = File(getPluginPath(), Platform.current().snykWrapperFileName)

fun getApplicationSettingsStateService(): SnykApplicationSettingsStateService = service()

fun getPluginPath() = PathManager.getPluginsPath() + "/snyk-intellij-plugin"

fun isProjectSettingsAvailable(project: Project?) = nonNull(project) && !project!!.isDefault

fun <L> getSyncPublisher(project: Project, topic: Topic<L>): L? {
    val messageBus = project.messageBus
    if (messageBus.isDisposed) return null
    return messageBus.syncPublisher(topic)
}

val <T> List<T>.tail: List<T>
    get() = drop(1)

val <T> List<T>.head: T
    get() = first()

fun isUrlValid(url: String?): Boolean {
    if (url == null || url.isEmpty()) {
        return true
    }

    return try {
        URL(url).toURI()

        true
    } catch (throwable: Throwable) {
        false
    }
}

fun isSnykCliRunning(project: Project): Boolean {
    val indicator = project.service<SnykTaskQueueService>().getCurrentProgressIndicator()
    return indicator != null && indicator.isRunning && !indicator.isCanceled
}

fun isSnykCodeRunning(project: Project): Boolean =
    AnalysisData.instance.isUpdateAnalysisInProgress(project) || RunUtils.instance.isFullRescanRequested(project)

fun isScanRunning(project: Project): Boolean = isSnykCliRunning(project) || isSnykCodeRunning(project)

fun isSnykCodeAvailable(customEndpointUrl: String?): Boolean =
    customEndpointUrl.isNullOrEmpty() || isSnykCodeSupportedEndpoint(customEndpointUrl)

fun toSnykCodeApiUrl(customEndpointUrl: String?): String =
    if (customEndpointUrl != null && isSnykCodeSupportedEndpoint(customEndpointUrl)) {
        customEndpointUrl
            .replace("https://", "https://deeproxy.")
            .removeSuffix("api")
    } else {
        "https://deeproxy.snyk.io/"
    }

private fun isSnykCodeSupportedEndpoint(customEndpointUrl: String) =
    customEndpointUrl == "https://dev.snyk.io/api" ||
    customEndpointUrl == "https://snyk.io/api"

fun getSnykCodeSettingsUrl(): String {
    val endpoint = getApplicationSettingsStateService().customEndpointUrl
    val baseUrl = if (endpoint.isNullOrEmpty()) {
        "https://app.snyk.io"
    } else {
        endpoint
            // example: https://snyk.io/api/ -> https://app.snyk.io
            .replace("https://", "https://app.")
            .replace(Regex("/+$"), "") // remove trailing slashes if any
            .removeSuffix("/api")
    }
    return "$baseUrl/manage/snyk-code"
}
