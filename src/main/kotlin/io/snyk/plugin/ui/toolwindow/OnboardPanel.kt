package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.panel
import icons.SnykIcons
import io.snyk.plugin.analytics.EventPropertiesProvider
import io.snyk.plugin.analytics.Segment
import io.snyk.plugin.events.SnykCliDownloadListener.Companion.CLI_DOWNLOAD_TOPIC
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.ui.settings.ScanTypesPanel
import javax.swing.SwingConstants

class OnboardPanel(project: Project) {
    private val disposable = Disposer.newDisposable()

    private val scanTypesPanel by lazy {
        return@lazy ScanTypesPanel(
            project,
            disposable,
            simplifyForOnboardPanel = true
        ).panel
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
            scanTypesPanel()
        }
        row {
            right {
                button("Analyze now!") { e ->
                    scanTypesPanel.apply()
                    Disposer.dispose(disposable)

                    service<SnykAnalyticsService>().logEvent(
                        Segment.Event.USER_TRIGGERS_ITS_FIRST_ANALYSIS,
                        EventPropertiesProvider.getSelectedProducts(getApplicationSettingsStateService())
                    )

                    getApplicationSettingsStateService().pluginFirstRun = false
                    getSyncPublisher(project, CLI_DOWNLOAD_TOPIC)?.checkCliExistsFinished()
                    project.service<SnykTaskQueueService>().scan()
                }
            }
        }
    }
}
