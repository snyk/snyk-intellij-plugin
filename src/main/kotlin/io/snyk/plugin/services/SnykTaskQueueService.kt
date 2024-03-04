package io.snyk.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getContainerService
import io.snyk.plugin.getIacService
import io.snyk.plugin.getOssService
import io.snyk.plugin.getSnykApiService
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykCode
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.isContainerEnabled
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.net.ClientException
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.SnykBalloonNotifications
import org.jetbrains.annotations.TestOnly
import snyk.common.SnykError
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded

@Service
class SnykTaskQueueService(val project: Project) {
    private val logger = logger<SnykTaskQueueService>()
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")
    private val taskQueueIac = BackgroundTaskQueue(project, "Snyk: Iac")
    private val taskQueueContainer = BackgroundTaskQueue(project, "Snyk: Container")

    private val settings
        get() = pluginSettings()

    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    var ossScanProgressIndicator: ProgressIndicator? = null
        private set

    var iacScanProgressIndicator: ProgressIndicator? = null
        private set

    var containerScanProgressIndicator: ProgressIndicator? = null
        private set

    @TestOnly
    fun getTaskQueue() = taskQueue

    fun scheduleRunnable(title: String, runnable: (indicator: ProgressIndicator) -> Unit) {
        taskQueue.run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                runnable.invoke(indicator)
            }
        })
    }

    fun connectProjectToLanguageServer(project: Project) {
        synchronized(LanguageServerWrapper) {
            val languageServerWrapper = LanguageServerWrapper.getInstance()
            if (!languageServerWrapper.isInitialized && !languageServerWrapper.isInitializing) {
                languageServerWrapper.initialize()
                val added = languageServerWrapper.getWorkspaceFolders(project)
                languageServerWrapper.updateWorkspaceFolders(added, emptySet())
            }
        }
    }

    fun scan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk: initializing...", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!confirmScanningAndSetWorkspaceTrustedStateIfNeeded(project)) return

                ApplicationManager.getApplication().invokeAndWait {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }
                indicator.checkCanceled()
                waitUntilCliDownloadedIfNeeded()
                indicator.checkCanceled()

                if (settings.snykCodeSecurityIssuesScanEnable || settings.snykCodeQualityIssuesScanEnable) {
                    if (!isSnykCodeLSEnabled()) {
                        scheduleSnykCodeScan()
                    } else {
                        LanguageServerWrapper.getInstance().sendScanCommand()
                    }
                }
                if (settings.ossScanEnable) {
                    scheduleOssScan()
                }
                if (isIacEnabled() && settings.iacScanEnabled) {
                    scheduleIacScan()
                }
                if (isContainerEnabled() && settings.containerScanEnabled) {
                    scheduleContainerScan()
                }
            }
        })
    }

    fun waitUntilCliDownloadedIfNeeded() {
        downloadLatestRelease()
        do {
            Thread.sleep(WAIT_FOR_DOWNLOAD_MILLIS)
        } while (isCliDownloading())
    }

    private fun scheduleContainerScan() {
        taskQueueContainer.run(object : Task.Backgroundable(project, "Snyk Container is scanning...", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!isCliInstalled()) return
                val snykCachedResults = getSnykCachedResults(project) ?: return
                if (snykCachedResults.currentContainerResult?.rescanNeeded == false) return
                logger.debug("Starting Container scan")
                containerScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                snykCachedResults.currentContainerResult = null
                val containerResult = try {
                    getContainerService(project)?.scan()
                } finally {
                    containerScanProgressIndicator = null
                }
                if (containerResult == null || project.isDisposed) return

                if (indicator.isCanceled) {
                    logger.debug("cancel container scan")
                    taskQueuePublisher?.stopped(wasContainerRunning = true)
                } else {
                    if (containerResult.isSuccessful()) {
                        logger.debug("Container result: ->")
                        containerResult.allCliIssues?.forEach {
                            logger.debug("  ${it.imageName}, ${it.vulnerabilities.size} issues")
                        }
                        scanPublisher?.scanningContainerFinished(containerResult)
                    } else {
                        scanPublisher?.scanningContainerError(containerResult.getFirstError()!!)
                    }
                }
                logger.debug("Container scan completed")
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        })
    }

    private fun scheduleSnykCodeScan() {
        object : Task.Backgroundable(project, "Checking if Snyk Code enabled for organisation...", true) {
            override fun run(indicator: ProgressIndicator) {
                if (settings.token.isNullOrBlank()) {
                    return
                }
                val sastCliConfigSettings = try {
                    getSnykApiService().getSastSettings()
                } catch (ignored: ClientException) {
                    null
                }

                settings.sastOnServerEnabled = sastCliConfigSettings?.sastEnabled
                settings.localCodeEngineEnabled = sastCliConfigSettings?.localCodeEngine?.enabled
                settings.localCodeEngineUrl = sastCliConfigSettings?.localCodeEngine?.url
                when (settings.sastOnServerEnabled) {
                    true -> {
                        getSnykCode(project)?.scan()
                        scanPublisher?.scanningStarted()
                    }

                    false -> {
                        SnykBalloonNotifications.showSastForOrgEnablement(project)
                        scanPublisher?.scanningSnykCodeError(
                            SnykError(SnykBalloonNotifications.sastForOrgEnablementMessage, project.basePath ?: "")
                        )
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                    }

                    null -> {
                        SnykBalloonNotifications.showNetworkErrorAlert(project)
                        scanPublisher?.scanningSnykCodeError(
                            SnykError(SnykBalloonNotifications.networkErrorAlertMessage, project.basePath ?: "")
                        )
                        // todo(artsiom): shell we do it `false` here?
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                    }
                }
            }
        }.queue()
    }

    private fun scheduleOssScan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk Open Source is scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!isCliInstalled()) return
                val snykCachedResults = getSnykCachedResults(project) ?: return
                if (snykCachedResults.currentOssResults != null) return

                ossScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                val ossResult = try {
                    getOssService(project)?.scan()
                } finally {
                    ossScanProgressIndicator = null
                }
                if (ossResult == null || project.isDisposed) return

                if (indicator.isCanceled) {
                    taskQueuePublisher?.stopped(wasOssRunning = true)
                } else {
                    if (ossResult.isSuccessful()) {
                        scanPublisher?.scanningOssFinished(ossResult)
                    } else {
                        ossResult.getFirstError()?.let { scanPublisher?.scanningOssError(it) }
                    }
                }
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        })
    }

    private fun scheduleIacScan() {
        taskQueueIac.run(object : Task.Backgroundable(project, "Snyk Infrastructure as Code is scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!isCliInstalled()) return
                val snykCachedResults = getSnykCachedResults(project) ?: return
                if (snykCachedResults.currentIacResult?.iacScanNeeded == false) return
                logger.debug("Starting IaC scan")
                iacScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                snykCachedResults.currentIacResult = null
                val iacResult = try {
                    getIacService(project)?.scan()
                } finally {
                    iacScanProgressIndicator = null
                }
                if (iacResult == null || project.isDisposed) return

                if (indicator.isCanceled) {
                    logger.debug("cancel IaC scan")
                    taskQueuePublisher?.stopped(wasIacRunning = true)
                } else {
                    if (iacResult.isSuccessful()) {
                        logger.debug("IaC result: ->")
                        iacResult.allCliIssues?.forEach {
                            logger.debug("  ${it.targetFile}, ${it.infrastructureAsCodeIssues.size} issues")
                        }
                        scanPublisher?.scanningIacFinished(iacResult)
                    } else {
                        val error = iacResult.getFirstError()
                        if (error == null) {
                            SnykError("unknown IaC error", project.basePath ?: "")
                        } else {
                            scanPublisher?.scanningIacError(error)
                        }
                    }
                }
                logger.debug("IaC scan completed")
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        })
    }

    fun downloadLatestRelease() {
        // abort even before submitting a task
        val cliDownloader = getSnykCliDownloaderService()
        if (!pluginSettings().manageBinariesAutomatically) {
            if (!isCliInstalled()) {
                val msg =
                    "The plugin cannot scan without Snyk CLI, but automatic download is disabled. " +
                        "Please put a Snyk CLI executable in ${pluginSettings().cliPath} and retry."
                SnykBalloonNotificationHelper.showError(msg, project)
                // no need to cancel the indicator here, as isCLIDownloading() will return false
            }
            // no need to cancel the indicator here, as isCliInstalled() will return false
            cliDownloader.stopCliDownload()
            return
        }

        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI presence", true) {
            override fun run(indicator: ProgressIndicator) {
                cliDownloadPublisher.checkCliExistsStarted()
                if (project.isDisposed) return

                if (!isCliInstalled()) {
                    cliDownloader.downloadLatestRelease(indicator, project)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator, project)
                }
                cliDownloadPublisher.checkCliExistsFinished()
            }
        })
    }

    fun stopScan() {
        val wasOssRunning = ossScanProgressIndicator?.isRunning == true
        ossScanProgressIndicator?.cancel()

        val wasSnykCodeRunning = isSnykCodeRunning(project)
        RunUtils.instance.cancelRunningIndicators(project)

        val wasIacRunning = iacScanProgressIndicator?.isRunning == true
        iacScanProgressIndicator?.cancel()

        val wasContainerRunning = containerScanProgressIndicator?.isRunning == true
        containerScanProgressIndicator?.cancel()

        taskQueuePublisher?.stopped(wasOssRunning, wasSnykCodeRunning, wasIacRunning, wasContainerRunning)
    }

    companion object {
        private const val WAIT_FOR_DOWNLOAD_MILLIS = 1000L
    }
}
