package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.extensions.SnykControllerImpl
import io.snyk.plugin.extensions.SnykControllerManager
import io.snyk.plugin.ui.SnykBalloonNotifications
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import snyk.common.AnnotatorCommon
import snyk.common.lsp.LanguageServerBulkFileListener

private const val EXTENSION_POINT_CONTROLLER_MANAGER =
  "io.snyk.snyk-intellij-plugin.controllerManager"

class SnykPostStartupActivity : ProjectActivity {

  private object ExtensionPointsUtil {
    val controllerManager =
      ExtensionPointName.create<SnykControllerManager>(EXTENSION_POINT_CONTROLLER_MANAGER)
  }

  private var listenersActivated = false
  val settings = pluginSettings()

  @Suppress("TooGenericExceptionCaught")
  override suspend fun execute(project: Project) {
    if (!listenersActivated) {
      val messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
      // TODO: add subscription for language server messages
      messageBusConnection.subscribe(
        VirtualFileManager.VFS_CHANGES,
        LanguageServerBulkFileListener(project),
      )
      messageBusConnection.subscribe(ProjectManager.TOPIC, SnykProjectManagerListener())
      listenersActivated = true
    }

    getSnykCachedResults(project)?.initCacheUpdater()

    project.service<AnnotatorCommon>().initRefreshing()

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      try {
        // this returns if no download possible (isDownloading == false)
        getSnykTaskQueueService(project)?.waitUntilCliDownloadedIfNeeded()

        if (isCliInstalled()) {
          getSnykTaskQueueService(project)?.connectProjectToLanguageServer(project)
        }
        getAnalyticsScanListener(project)?.initScanListener()
      } catch (e: Exception) {
        Logger.getInstance(SnykPostStartupActivity::class.java).warn(e)
      }
    }

    val feedbackRequestShownMoreThenTwoWeeksAgo =
      settings.lastTimeFeedbackRequestShown
        .toInstant()
        .plus(14, ChronoUnit.DAYS) // we'll give 2 weeks to evaluate initially
        .isBefore(Instant.now())
    if (settings.showFeedbackRequest && feedbackRequestShownMoreThenTwoWeeksAgo) {
      SnykBalloonNotifications.showFeedbackRequest(project)
      settings.lastTimeFeedbackRequestShown = Date.from(Instant.now())
    }

    ExtensionPointsUtil.controllerManager.extensionList.forEach {
      it.register(SnykControllerImpl(project))
    }
  }
}
