package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.panel
import io.snyk.plugin.analytics.getSelectedProducts
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.ui.settings.ScanTypesPanel
import snyk.analytics.AnalysisIsTriggered
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
            label("Welcome to Snyk, let's start by analysing your code.", bold = true).constraints(growX).apply {
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

                    service<SnykAnalyticsService>().logAnalysisIsTriggered(
                        AnalysisIsTriggered.builder()
                            .analysisType(getSelectedProducts(pluginSettings()))
                            .ide(AnalysisIsTriggered.Ide.JETBRAINS)
                            .triggeredByUser(true)
                            .build()
                    )

                    pluginSettings().pluginFirstRun = false
                    getSyncPublisher(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC)?.settingsChanged()
                    project.service<SnykTaskQueueService>().scan()
                }
            }
        }
    }
}
