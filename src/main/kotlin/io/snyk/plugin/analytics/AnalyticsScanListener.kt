package io.snyk.plugin.analytics

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.pluginSettings
import snyk.common.lsp.analytics.AnalyticsEvent

@Service(Service.Level.PROJECT)
class AnalyticsScanListener(val project: Project) {
    fun initScanListener() {
        if (!pluginSettings().pluginInstalledSent) {
            val event = AnalyticsEvent("plugin installed", listOf("install"))
            AnalyticsSender.getInstance(project).logEvent(event) {
                pluginSettings().pluginInstalledSent = true
            }
        }
    }
}
