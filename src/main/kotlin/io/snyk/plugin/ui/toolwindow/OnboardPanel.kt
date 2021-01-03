package io.snyk.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*
import icons.SnykIcons
import io.snyk.plugin.events.SnykCliDownloadListener.Companion.CLI_DOWNLOAD_TOPIC
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import io.snyk.plugin.ui.settings.ScanTypesPanel
import java.io.File
import javax.swing.SwingConstants

class OnboardPanel(project: Project) {
    private val scanTypesPanel by lazy {
        val allSupportedFilesCount = SnykCodeUtils.instance.getAllSupportedFilesInProject(project).size
        val allFilesCount = SnykCodeUtils.instance.allProjectFilesCount(project)
        return@lazy ScanTypesPanel(
            snykCodeScanComments = "We will upload and analyze $allSupportedFilesCount files " +
                "(${(100.0 * allSupportedFilesCount / allFilesCount).toInt()}%)" +
                " out of $allFilesCount files"
        ).panel
    }

    init {
        File(project.basePath + "/.dcignore").createNewFile()
    }

    val panel = panel {
        row {
            label("").constraints(growX).apply {
                component.icon = SnykIcons.LOGO
                component.horizontalAlignment = SwingConstants.CENTER
            }
        }
        row {
            label("Let's start analyzing your code", bold = true).constraints(growX).apply {
                component.horizontalAlignment = SwingConstants.CENTER
            }
        }
        row {
            label("We added a .dcignore file to upload only application's source code.").constraints(growX).apply {
                component.icon = AllIcons.General.NotificationInfo
                component.horizontalAlignment = SwingConstants.CENTER
            }
        }
        row {
            scanTypesPanel()
        }
        row {
            right {
                button("Analyze now!") { e ->
                    scanTypesPanel.apply()
                    getApplicationSettingsStateService().pluginFirstRun = false
                    project.messageBus.syncPublisher(CLI_DOWNLOAD_TOPIC).checkCliExistsFinished()
                    project.getService(SnykTaskQueueService::class.java).scan()
                }
            }
        }
    }
}
