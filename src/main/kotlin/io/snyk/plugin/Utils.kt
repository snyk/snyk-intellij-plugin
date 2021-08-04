package io.snyk.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.services.*
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import snyk.oss.OssService
import java.io.File
import java.net.URL
import java.util.Objects.nonNull

fun getOssService(project: Project): OssService = project.service()

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

fun isOssRunning(project: Project): Boolean {
    val indicator = project.service<SnykTaskQueueService>().getOssScanProgressIndicator()
    return indicator != null && indicator.isRunning && !indicator.isCanceled
}

fun isSnykCodeRunning(project: Project): Boolean =
    AnalysisData.instance.isUpdateAnalysisInProgress(project) || RunUtils.instance.isFullRescanRequested(project)

fun isScanRunning(project: Project): Boolean = isOssRunning(project) || isSnykCodeRunning(project)

fun isSnykCodeAvailable(customEndpointUrl: String?): Boolean =
    customEndpointUrl.isNullOrEmpty() || isSnykCodeSupportedEndpoint(customEndpointUrl)

fun toSnykCodeApiUrl(customEndpointUrl: String?): String =
    if (customEndpointUrl != null && isSnykCodeSupportedEndpoint(customEndpointUrl)) {
        customEndpointUrl
            .replace("https://", "https://deeproxy.")
            .removeTrailingSlashes()
            .removeSuffix("api")
    } else {
        "https://deeproxy.snyk.io/"
    }

private fun isSnykCodeSupportedEndpoint(customEndpointUrl: String) =
    customEndpointUrl.removeTrailingSlashes() == "https://dev.snyk.io/api" ||
    customEndpointUrl.removeTrailingSlashes() == "https://snyk.io/api"

fun getSnykCodeSettingsUrl(): String {
    val endpoint = getApplicationSettingsStateService().customEndpointUrl
    val baseUrl = if (endpoint.isNullOrEmpty()) {
        "https://app.snyk.io"
    } else {
        endpoint
            // example: https://snyk.io/api/ -> https://app.snyk.io
            .replace("https://", "https://app.")
            .removeTrailingSlashes()
            .removeSuffix("/api")
    }
    return "$baseUrl/manage/snyk-code"
}

private fun String.removeTrailingSlashes() : String = this.replace( Regex("/+$"), "")

// check sastEnablement in a loop with rising timeout
fun startSastEnablementCheckLoop(parentDisposable: Disposable, onSuccess: () -> Unit = {}) {
    val settings = getApplicationSettingsStateService()
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    var currentAttempt = 1
    val maxAttempts = 20
    lateinit var checkIfSastEnabled: () -> Unit
    checkIfSastEnabled = {
        if (settings.sastOnServerEnabled != true) {
            settings.sastOnServerEnabled = service<SnykApiService>().sastOnServerEnabled ?: false
            if (settings.sastOnServerEnabled == true) {
                onSuccess.invoke()
            } else if (!alarm.isDisposed && currentAttempt < maxAttempts) {
                currentAttempt++;
                alarm.addRequest(checkIfSastEnabled, 2000 * currentAttempt)
            }
        }
    }
    checkIfSastEnabled.invoke()
}


private val alarm = Alarm()

fun controlExternalProcessWithProgressIndicator(indicator: ProgressIndicator,
                                                onCancel: () -> Unit) {
    lateinit var checkCancelled: () -> Unit
    checkCancelled = {
        if (indicator.isCanceled) {
            onCancel()
        } else {
            alarm.addRequest(checkCancelled, 100)
        }
    }
    checkCancelled.invoke()
}
