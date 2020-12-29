package io.snyk.plugin.ui.settings

import com.intellij.ui.layout.panel
import io.snyk.plugin.getApplicationSettingsStateService

class ScanTypesPanel(
    private val cliScanComments: String? = null,
    private val snykCodeScanComments: String? = null
) {

    val panel = panel {
        row {
            checkBox(
                "Snyk OpenSource",
                { getApplicationSettingsStateService().cliScanEnable },
                { getApplicationSettingsStateService().cliScanEnable = it },
                cliScanComments
            )
        }
        row {
            checkBox(
                "Snyk Code",
                { getApplicationSettingsStateService().snykCodeScanEnable },
                { getApplicationSettingsStateService().snykCodeScanEnable = it },
                snykCodeScanComments
            )
        }
    }
}
