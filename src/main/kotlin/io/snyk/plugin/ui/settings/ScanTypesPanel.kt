package io.snyk.plugin.ui.settings

import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import io.snyk.plugin.getApplicationSettingsStateService

class ScanTypesPanel(
    private val cliScanComments: String? = null,
    private val snykCodeScanComments: String? = null
) {

    val panel = panel {
        row {
            checkBox(
                "Snyk OpenSource vulnerabilities",
                { getApplicationSettingsStateService().cliScanEnable },
                { getApplicationSettingsStateService().cliScanEnable = it },
                cliScanComments
            )
        }
        row {
            val snykCodeCheckbox = checkBox(
                "Snyk Code Security issues",
                { getApplicationSettingsStateService().snykCodeScanEnable },
                { getApplicationSettingsStateService().snykCodeScanEnable = it },
                snykCodeScanComments
            ).component
            checkBox(
                "Snyk Code Quality issues",
                { getApplicationSettingsStateService().snykCodeQualityIssuesScanEnable },
                { getApplicationSettingsStateService().snykCodeQualityIssuesScanEnable = it }
            )
                //.enableIf(snykCodeCheckbox.selected)
                .withLargeLeftGap()
        }
    }
}
